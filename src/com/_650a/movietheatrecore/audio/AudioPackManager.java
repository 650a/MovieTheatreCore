package com._650a.movietheatrecore.audio;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.bukkit.command.CommandSender;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.ffmpeg.FFprobeService;
import com._650a.movietheatrecore.media.MediaEntry;
import com._650a.movietheatrecore.resourcepack.EmbeddedPackServer;
import com._650a.movietheatrecore.resourcepack.ResourcePack;
import com._650a.movietheatrecore.util.Scheduler;
import com._650a.movietheatrecore.util.ZipUtil;

public class AudioPackManager {

    private final Main plugin;
    private final Configuration configuration;
    private final Scheduler scheduler;
    private final EmbeddedPackServer packServer;
    private final FFprobeService ffprobeService = new FFprobeService();
    private final Object buildLock = new Object();
    private boolean warnedMissingPackUrl = false;

    private final File packFolder;
    private final File packFile;

    public AudioPackManager(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
        this.scheduler = new Scheduler(plugin);
        this.packFolder = new File(configuration.getResourcePackFolder(), "pack");
        this.packFile = new File(configuration.getResourcePackFolder(), "pack.zip");
        this.packServer = new EmbeddedPackServer(plugin, configuration, configuration.getResourcePackFolder());
    }

    public void startServer() {
        packServer.start();
    }

    public AudioPreparation prepare(MediaEntry entry, File mediaFile) throws IOException {
        boolean hasAudio = hasAudioStream(mediaFile);
        if (!hasAudio) {
            entry.setAudioSha1(null);
            entry.setAudioChunks(0);
            return AudioPreparation.noAudio();
        }

        boolean entryChanged = ensureAudioChunks(entry, mediaFile);

        String packUrl = resolvePackUrl();
        if (packUrl == null || packUrl.isBlank()) {
            warnMissingPackUrl();
            return AudioPreparation.error("Resource pack URL not configured. Set resource_pack.server.public-url or pack.public-base-url to your HTTPS pack host.", null);
        }
        if (!configuration.resourcepack_server_enabled()) {
            return AudioPreparation.error("Pack server is disabled. Enable resource_pack.server.enabled to serve pack.zip.", null);
        }

        ensurePackReady(entryChanged);

        PackValidationResult validation = validatePackUrl(packUrl);
        logValidationWarnings(validation);
        if (validation.hasErrors()) {
            return AudioPreparation.error("Resource pack URL validation failed. Fix the pack host and try again.", validation);
        }

        int chunkCount = entry.getAudioChunks();
        if (chunkCount <= 0) {
            chunkCount = countChunks(entry);
            entry.setAudioChunks(chunkCount);
        }

        byte[] sha1 = decodeSha1(configuration.resourcepack_sha1());
        if (sha1 == null || sha1.length == 0) {
            return AudioPreparation.error("Resource pack SHA1 is missing. Rebuild the pack and try again.", validation);
        }
        return AudioPreparation.ready(new AudioTrack(entry.getId(), chunkCount, configuration.audio_chunk_seconds(), packUrl, sha1), validation);
    }

    public void rebuildPackAsync(CommandSender sender) {
        scheduler.runAsync(() -> {
            try {
                ensurePackReady(true);
                String sha1 = configuration.resourcepack_sha1();
                String url = resolvePackUrl();
                scheduler.runSync(() -> sender.sendMessage("Resource pack rebuilt. URL: " + (url == null ? "n/a" : url) + " SHA1: " + sha1));
            } catch (IOException e) {
                scheduler.runSync(() -> sender.sendMessage("Failed to rebuild resource pack: " + e.getMessage()));
            }
        });
    }

    public void rebuildPackAsync() {
        scheduler.runAsync(() -> {
            try {
                ensurePackReady(true);
            } catch (IOException e) {
                plugin.getLogger().warning("[MovieTheatreCore]: Failed to rebuild resource pack: " + e.getMessage());
            }
        });
    }

    public void stopAll() {
        packServer.stop();
    }

    public boolean isServerRunning() {
        return packServer.isRunning();
    }

    public String getServerError() {
        return packServer.getLastError();
    }

    public String getPackUrl() {
        return resolvePackUrl();
    }

    public String getPackSha1() {
        return configuration.resourcepack_sha1();
    }

    public long getLastBuildMillis() {
        return configuration.resourcepack_last_build();
    }

    public String getPackAssetsHash() {
        return configuration.resourcepack_assets_hash();
    }

    public EmbeddedPackServer getPackServer() {
        return packServer;
    }

    public PackDiagnostics getDiagnostics() {
        String baseUrl = configuration.pack_public_base_url();
        int port = configuration.resourcepack_server_port();
        String sha1 = configuration.resourcepack_sha1();
        long size = packFile.exists() ? packFile.length() : 0L;
        long lastBuild = configuration.resourcepack_last_build();
        String packUrl = resolvePackUrl();
        String curlUrl = packUrl == null ? null : "curl -I \"" + packUrl + "\"";
        return new PackDiagnostics(baseUrl, port, sha1, size, lastBuild, packUrl, curlUrl);
    }

    public PackValidationResult validatePackUrl() {
        return validatePackUrl(resolvePackUrl());
    }

    private void warnMissingPackUrl() {
        if (!warnedMissingPackUrl) {
            plugin.getLogger().warning("[MovieTheatreCore]: Resource pack URL not configured. Set resource_pack.server.public-url or pack.public-base-url to your HTTPS host.");
            warnedMissingPackUrl = true;
        }
    }

    private void ensurePackReady(boolean force) throws IOException {
        synchronized (buildLock) {
            String assetsHash = computeAssetsHash();
            boolean missing = !packFile.exists();
            boolean dirty = force || missing || !assetsHash.equals(configuration.resourcepack_assets_hash());
            if (!dirty) {
                return;
            }
            buildPack(assetsHash);
        }
    }

    private boolean ensureAudioChunks(MediaEntry entry, File mediaFile) throws IOException {
        File chunkFolder = getAudioChunksFolder(entry);
        File[] chunks = chunkFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
        boolean missing = chunks == null || chunks.length == 0;
        if (missing) {
            if (chunkFolder.exists()) {
                FileUtils.deleteDirectory(chunkFolder);
            }
            chunkFolder.mkdirs();
            extractChunks(mediaFile, chunkFolder);
        }

        String signature = computeChunkSignature(chunkFolder);
        boolean changed = signature != null && !signature.equals(entry.getAudioSha1());
        if (signature != null) {
            entry.setAudioSha1(signature);
        }

        int count = countChunks(entry);
        entry.setAudioChunks(count);
        return missing || changed;
    }

    private boolean hasAudioStream(File mediaFile) throws IOException {
        FFprobeService.ProbeResult probe = ffprobeService.probe(mediaFile);
        return probe.audioStreams > 0;
    }

    private File getAudioChunksFolder(MediaEntry entry) {
        return new File(configuration.getAudioChunksFolder(), entry.getId());
    }

    private void buildPack(String assetsHash) throws IOException {
        if (packFolder.exists()) {
            FileUtils.deleteDirectory(packFolder);
        }
        packFolder.mkdirs();

        createPackMetadata(packFolder);
        Map<String, Object> soundsMap = new HashMap<>();
        copyChunksToPack(packFolder, soundsMap);
        writeSoundsJson(packFolder, soundsMap);

        File tempZip = new File(configuration.getTempDir(), "pack.zip.tmp");
        ZipUtil.zipDirectory(packFolder.toPath(), tempZip.toPath());
        Files.move(tempZip.toPath(), packFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        String sha1 = computeSha1(packFile);
        configuration.set_resourcepack_sha1(sha1);
        configuration.set_resourcepack_assets_hash(assetsHash);
        configuration.set_resourcepack_last_build(System.currentTimeMillis());

        String url = resolvePackUrl();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(configuration.resourcepack_last_build()));
        plugin.getLogger().info("[MovieTheatreCore]: Resource pack built at " + timestamp + " (SHA1: " + sha1 + ", url: " + (url == null ? "n/a" : url) + ").");
    }

    private void createPackMetadata(File packFolder) throws IOException {
        File metaFile = new File(packFolder, "pack.mcmeta");
        Map<String, Object> pack = new HashMap<>();
        pack.put("pack_format", new ResourcePack().getResourcePackFormat());
        pack.put("description", "MovieTheatreCore audio pack");

        Map<String, Object> root = new HashMap<>();
        root.put("pack", pack);

        try (Writer writer = new FileWriter(metaFile)) {
            new Gson().toJson(root, writer);
        }

        File iconFile = new File(packFolder, "pack.png");
        try {
            java.net.URL packUrl = Main.class.getResource("resources/audio.png");
            java.awt.image.BufferedImage buffered = packUrl == null
                    ? new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    : javax.imageio.ImageIO.read(packUrl);
            javax.imageio.ImageIO.write(buffered, "png", iconFile);
        } catch (IOException e) {
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", iconFile);
        }
    }

    private void copyChunksToPack(File packFolder, Map<String, Object> soundsMap) throws IOException {
        File chunkRoot = configuration.getAudioChunksFolder();
        File[] entries = chunkRoot.listFiles(File::isDirectory);
        if (entries == null) {
            return;
        }
        Arrays.sort(entries, Comparator.comparing(File::getName));
        File soundsDir = new File(packFolder, "assets/movietheatrecore/sounds");
        soundsDir.mkdirs();

        for (File entryFolder : entries) {
            File[] chunks = entryFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
            if (chunks == null) {
                continue;
            }
            Arrays.sort(chunks, Comparator.comparing(File::getName));
            File entryTarget = new File(soundsDir, entryFolder.getName());
            entryTarget.mkdirs();

            for (File chunk : chunks) {
                Files.copy(chunk.toPath(), new File(entryTarget, chunk.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                String chunkName = chunk.getName().replace(".ogg", "");
                Map<String, Object> soundDef = new HashMap<>();
                List<String> sounds = new ArrayList<>();
                sounds.add("movietheatrecore:" + entryFolder.getName() + "/" + chunkName);
                soundDef.put("sounds", sounds);
                soundsMap.put("movietheatrecore." + entryFolder.getName() + "." + chunkName, soundDef);
            }
        }
    }

    private void writeSoundsJson(File packFolder, Map<String, Object> soundsMap) throws IOException {
        File soundsJson = new File(packFolder, "assets/movietheatrecore/sounds.json");
        soundsJson.getParentFile().mkdirs();
        try (Writer writer = new FileWriter(soundsJson)) {
            new Gson().toJson(soundsMap, writer);
        }
    }

    private String resolvePackUrl() {
        String baseUrl = resolvePackBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + "pack.zip";
    }

    private String resolvePackBaseUrl() {
        String serverUrl = normalizeBaseUrl(configuration.resourcepack_server_public_url());
        if (isUsableBaseUrl(serverUrl)) {
            return serverUrl;
        }
        String configured = normalizeBaseUrl(configuration.pack_public_base_url());
        if (isUsableBaseUrl(configured)) {
            return configured;
        }
        String host = normalizeBaseUrl(configuration.resourcepack_host_url());
        if (isUsableBaseUrl(host)) {
            return host;
        }
        return null;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/pack.zip")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/pack.zip".length());
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean isUsableBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        if (isBlockedHost(baseUrl)) {
            if (configuration.debug_pack()) {
                plugin.getLogger().warning("[MovieTheatreCore]: Ignoring pack URL pointing at 0.0.0.0: " + baseUrl);
            }
            return false;
        }
        return true;
    }

    private boolean isBlockedHost(String baseUrl) {
        try {
            URL url = new URL(baseUrl);
            return "0.0.0.0".equals(url.getHost());
        } catch (Exception ignored) {
            return baseUrl.contains("0.0.0.0");
        }
    }

    private PackValidationResult validatePackUrl(String packUrl) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (packUrl == null || packUrl.isBlank()) {
            errors.add("Pack URL is missing.");
            return new PackValidationResult(warnings, errors);
        }
        try {
            URL url = new URL(packUrl);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                warnings.add("Pack URL is not HTTPS. Public packs should be served over HTTPS.");
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            int status = connection.getResponseCode();
            if (status == 405) {
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Range", "bytes=0-0");
                status = connection.getResponseCode();
            }
            String contentType = connection.getHeaderField("Content-Type");
            String location = connection.getHeaderField("Location");

            if (status >= 300 && status < 400) {
                warnings.add("Pack URL redirected (" + status + ") to: " + location);
            } else if (status >= 400) {
                errors.add("Pack URL returned HTTP " + status + ".");
            }

            if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("text/html")) {
                errors.add("Pack URL returned HTML instead of a ZIP file.");
            } else if (contentType != null && !contentType.isBlank()
                    && !contentType.toLowerCase(java.util.Locale.ROOT).contains("zip")
                    && !contentType.toLowerCase(java.util.Locale.ROOT).contains("octet-stream")) {
                warnings.add("Pack content-type is " + contentType + " (expected application/zip).");
            }
        } catch (IOException e) {
            errors.add("Pack URL unreachable: " + e.getMessage());
        }
        return new PackValidationResult(warnings, errors);
    }

    private void logValidationWarnings(PackValidationResult result) {
        if (result == null || result.warnings().isEmpty()) {
            return;
        }
        for (String warning : result.warnings()) {
            plugin.getLogger().warning("[MovieTheatreCore]: Pack check warning: " + warning);
        }
    }

    private void extractChunks(File mediaFile, File chunkFolder) throws IOException {
        String codec = configuration.audio_codec();
        String codecArg = codec.equalsIgnoreCase("vorbis") ? "libvorbis" : codec;
        String output = new File(chunkFolder, "chunk_%03d.ogg").getAbsolutePath();

        String[] command = {
                plugin.getFfmpeg().getExecutablePath(),
                "-hide_banner",
                "-loglevel", "error",
                "-i", mediaFile.getAbsolutePath(),
                "-vn",
                "-c:a", codecArg,
                "-ar", String.valueOf(configuration.audio_sample_rate()),
                "-f", "segment",
                "-segment_time", String.valueOf(configuration.audio_chunk_seconds()),
                "-reset_timestamps", "1",
                output
        };

        try {
            Process process = new ProcessBuilder(command).start();
            plugin.getProcess().add(process);
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Audio extraction interrupted.", e);
        }
    }

    private int countChunks(MediaEntry entry) {
        File chunkFolder = getAudioChunksFolder(entry);
        File[] chunks = chunkFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
        return chunks == null ? 0 : chunks.length;
    }

    private String computeAssetsHash() throws IOException {
        MessageDigest digest = getSha1Digest();
        File chunkRoot = configuration.getAudioChunksFolder();
        File[] entries = chunkRoot.listFiles(File::isDirectory);
        if (entries == null) {
            return "";
        }
        Arrays.sort(entries, Comparator.comparing(File::getName));
        for (File entryFolder : entries) {
            digest.update(entryFolder.getName().getBytes(StandardCharsets.UTF_8));
            File[] chunks = entryFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
            if (chunks == null) {
                continue;
            }
            Arrays.sort(chunks, Comparator.comparing(File::getName));
            for (File chunk : chunks) {
                digest.update(chunk.getName().getBytes(StandardCharsets.UTF_8));
                digest.update(Long.toString(chunk.length()).getBytes(StandardCharsets.UTF_8));
                digest.update(Long.toString(chunk.lastModified()).getBytes(StandardCharsets.UTF_8));
            }
        }
        return toHex(digest.digest());
    }

    private String computeChunkSignature(File chunkFolder) throws IOException {
        MessageDigest digest = getSha1Digest();
        File[] chunks = chunkFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
        if (chunks == null) {
            return "";
        }
        Arrays.sort(chunks, Comparator.comparing(File::getName));
        for (File chunk : chunks) {
            digest.update(chunk.getName().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(chunk.length()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(chunk.lastModified()).getBytes(StandardCharsets.UTF_8));
        }
        return toHex(digest.digest());
    }

    private MessageDigest getSha1Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 not available.", e);
        }
    }

    private String computeSha1(File file) throws IOException {
        MessageDigest digest = getSha1Digest();
        byte[] data = Files.readAllBytes(file.toPath());
        byte[] hashed = digest.digest(data);
        return toHex(hashed);
    }

    private String toHex(byte[] hashed) {
        StringBuilder builder = new StringBuilder();
        for (byte b : hashed) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private byte[] decodeSha1(String sha1) {
        if (sha1 == null || sha1.isEmpty()) {
            return new byte[0];
        }
        int len = sha1.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(sha1.charAt(i), 16) << 4)
                    + Character.digit(sha1.charAt(i + 1), 16));
        }
        return data;
    }

    public record PackDiagnostics(String publicBaseUrl, int internalPort, String sha1, long packSize, long lastBuild,
                                   String packUrl, String curlUrl) {
    }

    public record PackValidationResult(List<String> warnings, List<String> errors) {
        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
    }

    public record AudioPreparation(boolean hasAudio, AudioTrack track, PackValidationResult validation, String error) {
        public static AudioPreparation noAudio() {
            return new AudioPreparation(false, null, null, null);
        }

        public static AudioPreparation ready(AudioTrack track, PackValidationResult validation) {
            return new AudioPreparation(true, track, validation, null);
        }

        public static AudioPreparation error(String message, PackValidationResult validation) {
            return new AudioPreparation(true, null, validation, message);
        }

        public boolean isReady() {
            return track != null && error == null;
        }
    }
}
