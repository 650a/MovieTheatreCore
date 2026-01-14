package com._650a.movietheatrecore.resourcepack;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;

public class EmbeddedPackServer {

    private final Main plugin;
    private final Configuration configuration;
    private final File rootFolder;
    private final File packFolder;

    private HttpServer server;
    private String lastError;
    private String boundHost;
    private int boundPort;
    private String publicBaseUrl;

    public EmbeddedPackServer(Main plugin, Configuration configuration, File rootFolder) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.rootFolder = rootFolder;
        this.packFolder = new File(rootFolder, "pack");
    }

    public synchronized boolean start() {
        if (!configuration.resourcepack_server_enabled()) {
            return false;
        }
        if (server != null) {
            return true;
        }

        boundHost = normalizeHost(configuration.resourcepack_server_bind());
        boundPort = configuration.resourcepack_server_port();

        try {
            server = HttpServer.create(new InetSocketAddress(boundHost, boundPort), 0);
            server.createContext("/", new FileHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            publicBaseUrl = resolvePublicBaseUrl();
            lastError = null;
            plugin.getLogger().info("[MovieTheatreCore]: Pack server started on " + boundHost + ":" + boundPort + ".");
            return true;
        } catch (BindException e) {
            lastError = "Port already in use (" + boundPort + ").";
            plugin.getLogger().warning("[MovieTheatreCore]: Failed to start pack server: " + lastError);
        } catch (IOException e) {
            lastError = e.getMessage();
            plugin.getLogger().warning("[MovieTheatreCore]: Failed to start pack server: " + e.getMessage());
        }
        server = null;
        return false;
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            plugin.getLogger().info("[MovieTheatreCore]: Pack server stopped.");
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public String getLastError() {
        return lastError;
    }

    public String getBoundHost() {
        return boundHost;
    }

    public int getBoundPort() {
        return boundPort;
    }

    public String getPublicBaseUrl() {
        if (publicBaseUrl == null) {
            publicBaseUrl = resolvePublicBaseUrl();
        }
        return publicBaseUrl;
    }

    public String buildUrl(String path) {
        String base = getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return base + normalized;
    }

    private String resolvePublicBaseUrl() {
        return normalizePublicBaseUrl(configuration.resolveResourcePackBaseUrl());
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizePublicBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = trimTrailingSlash(value.trim());
        if (trimmed.endsWith("/pack.zip")) {
            trimmed = trimTrailingSlash(trimmed.substring(0, trimmed.length() - "/pack.zip".length()));
        }
        if (isBlockedHost(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private boolean isBlockedHost(String baseUrl) {
        try {
            java.net.URL url = new java.net.URL(baseUrl);
            return "0.0.0.0".equals(url.getHost());
        } catch (Exception ignored) {
            return baseUrl.contains("0.0.0.0");
        }
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "0.0.0.0";
        }
        return host.trim();
    }

    private class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path != null && path.equals("/health")) {
                byte[] response = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.getResponseHeaders().add("Cache-Control", "no-store");
                if ("HEAD".equalsIgnoreCase(method)) {
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(response.length));
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
                return;
            }
            if (path == null || path.isBlank() || "/".equals(path)) {
                byte[] response = "MovieTheatreCore pack server".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.getResponseHeaders().add("Cache-Control", "no-store");
                if ("HEAD".equalsIgnoreCase(method)) {
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(response.length));
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
                return;
            }

            File target = resolveFile(path);
            if (target == null || !target.exists() || !target.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            Path targetPath = target.toPath();
            exchange.getResponseHeaders().add("Content-Type", contentType(targetPath));
            exchange.getResponseHeaders().add("Cache-Control", "no-store");
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(target.length()));
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, target.length());
            try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        }

        private File resolveFile(String path) throws IOException {
            File base = rootFolder;
            String resolvedPath = path.startsWith("/") ? path.substring(1) : path;

            if (path.startsWith("/assets/")) {
                base = packFolder;
                resolvedPath = path.substring(1);
            }

            File candidate = new File(base, resolvedPath);
            Path basePath = base.toPath().toAbsolutePath().normalize();
            Path filePath = candidate.toPath().toAbsolutePath().normalize();
            if (!filePath.startsWith(basePath)) {
                return null;
            }
            return filePath.toFile();
        }

        private String contentType(Path target) throws IOException {
            String name = target.getFileName().toString().toLowerCase();
            if (name.endsWith(".zip")) {
                return "application/zip";
            }
            if (name.endsWith(".ogg")) {
                return "audio/ogg";
            }
            if (name.endsWith(".png")) {
                return "image/png";
            }
            if (name.endsWith(".json") || name.endsWith(".mcmeta")) {
                return "application/json";
            }
            String detected = Files.probeContentType(target);
            return detected == null ? "application/octet-stream" : detected;
        }
    }
}
