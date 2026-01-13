package com._650a.movietheatrecore.media;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.audio.AudioPackManager;
import com._650a.movietheatrecore.audio.AudioTrack;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.dependency.DependencyManager;
import com._650a.movietheatrecore.playback.PlaybackOptions;
import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.tasks.TaskAsyncLoadConfigurations;
import com._650a.movietheatrecore.util.Scheduler;
import com._650a.movietheatrecore.video.Video;

public class MediaManager {

    private final Main plugin;
    private final Configuration configuration;
    private final MediaLibrary library;
    private final MediaCacheManager cacheManager;
    private final AudioPackManager audioPackManager;
    private final Scheduler scheduler;
    private Integer lastResolverExitCode;
    private String lastResolverError;

    public MediaManager(Main plugin, MediaLibrary library, AudioPackManager audioPackManager) {
        this.plugin = plugin;
        this.configuration = new Configuration();
        this.library = library;
        this.cacheManager = new MediaCacheManager(configuration);
        this.audioPackManager = audioPackManager;
        this.scheduler = new Scheduler(plugin);
    }

    public void addMedia(CommandSender sender, String name, String url, String customId) {
        if (library.getEntry(name) != null) {
            sender.sendMessage(ChatColor.RED + "Media with that name already exists.");
            return;
        }
        if (!isSafeIdentifier(name)) {
            sender.sendMessage(ChatColor.RED + "Media name contains invalid characters. Use letters, numbers, dashes, or underscores.");
            return;
        }
        String resolvedCustomId = normalizeIdentifier(customId);
        if (resolvedCustomId != null) {
            if (!isSafeIdentifier(resolvedCustomId)) {
                sender.sendMessage(ChatColor.RED + "Media ID contains invalid characters. Use letters, numbers, dashes, or underscores.");
                return;
            }
            if (library.isIdInUse(resolvedCustomId)) {
                sender.sendMessage(ChatColor.RED + "Media ID already in use. Choose a different ID.");
                return;
            }
        }
        scheduler.runAsync(() -> {
            String resolved = resolveAndValidateUrl(sender, url);
            if (resolved == null) {
                return;
            }
            DependencyManager.ResolvedBinary ffprobe = plugin.getDependencyManager().resolveBinary(DependencyManager.BinaryType.FFPROBE, true);
            if (ffprobe == null || !ffprobe.isValid()) {
                scheduler.runSync(() -> sender.sendMessage(configuration.libraries_not_installed()));
                return;
            }

            try {
                MediaEntry entry = downloadEntry(name, resolved, resolvedCustomId, true);
                File videoFile = ensureVideoFile(entry);
                File configFile = getVideoConfigFile(entry);
                Video video = new Video(configFile);
                if (!configFile.exists()) {
                    video.createConfiguration(videoFile);
                }
                AudioPackManager.AudioPreparation preparation = audioPackManager.prepare(entry, videoFile);
                library.save();
                if (preparation.error() != null) {
                    scheduler.runSync(() -> sender.sendMessage(ChatColor.YELLOW + "Media added, but audio pack not ready: " + preparation.error()));
                }
                library.addEntry(entry);
                reloadVideos();
                scheduler.runSync(() -> sender.sendMessage(ChatColor.GREEN + "Media added: " + entry.getName()));
            } catch (IOException e) {
                scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "Failed to download media: " + e.getMessage()));
            } catch (org.bukkit.configuration.InvalidConfigurationException e) {
                scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "Failed to write media metadata: " + e.getMessage()));
            }
        });
    }

    public void removeMedia(CommandSender sender, String name) {
        MediaEntry entry = library.getEntry(name);
        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "Unknown media: " + name);
            return;
        }

        scheduler.runAsync(() -> {
            try {
                deleteEntryFiles(entry);
                library.removeEntry(name);
                reloadVideos();
                scheduler.runSync(() -> sender.sendMessage(ChatColor.GREEN + "Media removed: " + name));
            } catch (IOException e) {
                scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "Failed to remove media: " + e.getMessage()));
            }
        });
    }

    public void listMedia(CommandSender sender) {
        List<MediaEntry> entries = library.listEntries();
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No media entries.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Media library:");
        for (MediaEntry entry : entries) {
            sender.sendMessage(ChatColor.GRAY + "- " + entry.getName() + " (" + entry.getUrl() + ")");
        }
    }

    public void playMedia(CommandSender sender, Screen screen, String name) {
        MediaEntry entry = library.getEntry(name);
        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "Unknown media: " + name);
            return;
        }
        playEntry(sender, screen, entry);
    }

    public void prepareMediaPlayback(String name, java.util.function.Consumer<MediaPlayback> onReady, java.util.function.Consumer<String> onError) {
        MediaEntry entry = library.getEntry(name);
        if (entry == null) {
            if (onError != null) {
                onError.accept("Unknown media: " + name);
            }
            return;
        }

        scheduler.runAsync(() -> {
            try {
                cacheManager.touch(entry);
                library.save();
                File videoFile = ensureVideoFile(entry);
                File configFile = getVideoConfigFile(entry);
                Video video = new Video(configFile);
                if (!configFile.exists()) {
                    DependencyManager.ResolvedBinary ffprobe = plugin.getDependencyManager().resolveBinary(DependencyManager.BinaryType.FFPROBE, true);
                    if (ffprobe == null || !ffprobe.isValid()) {
                        scheduler.runSync(() -> {
                            if (onError != null) {
                                onError.accept(configuration.libraries_not_installed());
                            }
                        });
                        return;
                    }
                    video.createConfiguration(videoFile);
                }
                if (!video.isLoaded()) {
                    video.load();
                    scheduler.runSync(() -> {
                        if (onError != null) {
                            onError.accept("Media is loading. Try again shortly.");
                        }
                    });
                    return;
                }
                AudioPackManager.AudioPreparation preparation = audioPackManager.prepare(entry, videoFile);
                library.save();
                if (preparation.error() != null) {
                    scheduler.runSync(() -> {
                        if (onError != null) {
                            onError.accept(preparation.error());
                        }
                    });
                    return;
                }
                AudioTrack track = preparation.track();
                boolean allowAudio = preparation.isReady();
                PlaybackOptions options = new PlaybackOptions(allowAudio, entry, track);
                MediaPlayback playback = new MediaPlayback(video, options, entry);
                scheduler.runSync(() -> {
                    if (onReady != null) {
                        onReady.accept(playback);
                    }
                });
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                scheduler.runSync(() -> {
                    if (onError != null) {
                        onError.accept("Failed to prepare media: " + e.getMessage());
                    }
                });
            }
        });
    }

    public void playUrl(CommandSender sender, Screen screen, String url) {
        scheduler.runAsync(() -> {
            String resolved = resolveAndValidateUrl(sender, url);
            if (resolved == null) {
                return;
            }
            String urlHash = Integer.toHexString(resolved.hashCode());
            MediaEntry entry = library.getCachedByUrl(urlHash);
            if (entry != null && cacheManager.getCacheFile(entry).exists()) {
                scheduler.runSync(() -> playEntry(sender, screen, entry));
                return;
            }

            try {
                String tempName = "url-" + urlHash;
                MediaEntry downloaded = downloadEntry(tempName, resolved, null, false);
                library.addUrlCache(urlHash, downloaded);
                ensureVideoFile(downloaded);
                reloadVideos();
                scheduler.runSync(() -> playEntry(sender, screen, downloaded));
            } catch (IOException e) {
                scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "Failed to download URL: " + e.getMessage()));
            }
        });
    }

    private MediaEntry downloadEntry(String name, String url, String customId, boolean libraryEntry) throws IOException {
        String extension = FilenameUtils.getExtension(url);
        String entryId = (customId == null || customId.isBlank()) ? UUID.randomUUID().toString() : customId;
        MediaEntry entry = new MediaEntry(name, url, entryId, extension, libraryEntry);
        long maxBytes = configuration.media_max_download_mb() * 1024L * 1024L;
        MediaEntry downloaded = cacheManager.download(entry, maxBytes, configuration.media_download_timeout_seconds());
        cacheManager.touch(downloaded);
        library.save();
        cacheManager.evictIfNeeded(library.listAllCached(), configuration.media_cache_max_gb() * 1024L * 1024L * 1024L, Set.of(downloaded.getId()));
        return downloaded;
    }

    private void playEntry(CommandSender sender, Screen screen, MediaEntry entry) {
        scheduler.runAsync(() -> {
            try {
                cacheManager.touch(entry);
                library.save();
                File videoFile = ensureVideoFile(entry);
                File configFile = getVideoConfigFile(entry);
                Video video = new Video(configFile);
                if (!configFile.exists()) {
                    DependencyManager.ResolvedBinary ffprobe = plugin.getDependencyManager().resolveBinary(DependencyManager.BinaryType.FFPROBE, true);
                    if (ffprobe == null || !ffprobe.isValid()) {
                        scheduler.runSync(() -> sender.sendMessage(configuration.libraries_not_installed()));
                        return;
                    }
                    video.createConfiguration(videoFile);
                }
                if (!video.isLoaded()) {
                    video.load();
                    scheduler.runSync(() -> sender.sendMessage(ChatColor.YELLOW + "Media is loading. Try again shortly."));
                    return;
                }
                AudioPackManager.AudioPreparation preparation = audioPackManager.prepare(entry, videoFile);
                library.save();
                if (preparation.error() != null) {
                    scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + preparation.error()));
                    return;
                }
                AudioTrack track = preparation.track();
                boolean allowAudio = preparation.isReady();
                PlaybackOptions options = new PlaybackOptions(allowAudio, entry, track);
                scheduler.runSync(() -> plugin.getPlaybackManager().start(screen, video, options));
                scheduler.runSync(() -> sender.sendMessage(ChatColor.GREEN + "Playing media " + entry.getName() + " on " + screen.getName() + "."));
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "Failed to play media: " + e.getMessage()));
            }
        });
    }

    private File ensureVideoFile(MediaEntry entry) throws IOException {
        File cacheFile = cacheManager.getCacheFile(entry);
        if (!cacheFile.exists()) {
            throw new IOException("Cached media missing.");
        }
        File target = new File(configuration.getVideosFolder(), entry.getName() + "." + entry.getExtension());
        File temp = new File(configuration.getTempDir(), entry.getName() + "." + entry.getExtension() + ".tmp");
        FileUtils.copyFile(cacheFile, temp);
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private File getVideoConfigFile(MediaEntry entry) {
        return new File(configuration.getVideosFolder() + "/" + entry.getName(), entry.getName() + ".yml");
    }

    private void deleteEntryFiles(MediaEntry entry) throws IOException {
        File cache = cacheManager.getCacheFile(entry);
        if (cache.exists()) {
            cache.delete();
        }
        File videoFile = new File(configuration.getVideosFolder(), entry.getName() + "." + entry.getExtension());
        if (videoFile.exists()) {
            videoFile.delete();
        }
        File configFolder = new File(configuration.getVideosFolder(), entry.getName());
        if (configFolder.exists()) {
            FileUtils.deleteDirectory(configFolder);
        }
        File audioFolder = new File(configuration.getAudioChunksFolder(), entry.getId());
        if (audioFolder.exists()) {
            FileUtils.deleteDirectory(audioFolder);
        }
        audioPackManager.rebuildPackAsync();
    }

    private void reloadVideos() {
        scheduler.runSync(() -> new TaskAsyncLoadConfigurations().runTaskAsynchronously(plugin));
    }

    private String resolveAndValidateUrl(CommandSender sender, String url) {
        String resolved = resolveUrl(sender, url);
        if (resolved == null) {
            return null;
        }
        if (!isAllowedUrl(resolved)) {
            scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "URL not allowed by sources.allowlist-mode (STRICT)."));
            return null;
        }
        String error = validateMediaUrl(resolved);
        if (error != null) {
            scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + error));
            return null;
        }
        return resolved;
    }

    private String validateMediaUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return "Invalid URL format. Use a YouTube link, direct MP4/WEBM/M3U8, or MediaFire direct download link.";
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return "Only http/https URLs are supported.";
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "URL must include a valid host.";
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String extension = FilenameUtils.getExtension(uri.getPath()).toLowerCase(Locale.ROOT);
        boolean extensionAllowed = extension.equals("mp4") || extension.equals("webm") || extension.equals("m3u8");

        MediaHeadInfo head = fetchHeadInfo(url);
        if (head != null && head.statusCode >= 200 && head.statusCode < 400) {
            String contentType = head.contentType == null ? "" : head.contentType.toLowerCase(Locale.ROOT);
            boolean contentTypeVideo = contentType.contains("video/mp4") || contentType.contains("video/webm");
            boolean contentTypeStream = contentType.contains("application/vnd.apple.mpegurl")
                    || contentType.contains("application/x-mpegurl");
            boolean contentTypeHtml = contentType.contains("text/html");
            if (contentTypeVideo) {
                return null;
            }
            if (contentTypeStream) {
                return null;
            }
            if (contentTypeHtml) {
                return "URL does not point to a downloadable MP4/WEBM/M3U8 stream.";
            }
            if (extensionAllowed) {
                return null;
            }
        }

        if (extensionAllowed || normalizedHost.contains("googlevideo.com")) {
            return null;
        }

        return "URL must point to a direct MP4/WEBM/M3U8 file (including MediaFire direct links).";
    }

    private String resolveUrl(CommandSender sender, String url) {
        if (!isYoutubeUrl(url)) {
            return url;
        }
        DependencyManager dependencyManager = plugin.getDependencyManager();
        DependencyManager.ResolvedBinary resolver = dependencyManager.resolveBinary(DependencyManager.BinaryType.YT_DLP, true);
        if (resolver == null || !resolver.isValid() || resolver.getPath() == null) {
            scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "YouTube resolver not available. Install yt-dlp or enable auto-install, then run /mtc deps status."));
            lastResolverExitCode = null;
            lastResolverError = resolver == null ? "yt-dlp not resolved" : resolver.getError();
            return null;
        }
        try {
            List<String> command = new java.util.ArrayList<>();
            command.add(resolver.getPath().toString());
            File cookiesFile = resolveCookiesFile();
            CookieStatus cookieStatus = null;
            boolean cookiesUsed = false;
            if (cookiesFile != null && cookiesFile.exists() && cookiesFile.canRead()) {
                cookieStatus = evaluateCookies(cookiesFile);
                if (cookieStatus.expired) {
                    scheduler.runSync(() -> sender.sendMessage(ChatColor.YELLOW + "YouTube cookies appear expired. Export a fresh youtube-cookies.txt before playback fails."));
                } else if (!cookieStatus.hasEntries) {
                    scheduler.runSync(() -> sender.sendMessage(ChatColor.YELLOW + "YouTube cookies file is empty. Export a new youtube-cookies.txt if playback fails."));
                }
                command.add("--cookies");
                command.add(cookiesFile.getAbsolutePath());
                cookiesUsed = true;
            } else if (configuration.youtube_require_cookies()) {
                String path = cookiesFile == null ? "youtube-cookies.txt" : cookiesFile.getPath();
                scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "Cookies file required but missing/unreadable: " + path));
                lastResolverExitCode = null;
                lastResolverError = "cookies file required";
                return null;
            } else {
                scheduler.runSync(() -> sender.sendMessage(ChatColor.YELLOW + "Running yt-dlp without cookies. If YouTube blocks playback, add youtube-cookies.txt to the plugin folder."));
            }
            if (configuration.youtube_use_js_runtime()) {
                DependencyManager.ResolvedBinary deno = dependencyManager.resolveBinary(DependencyManager.BinaryType.DENO, true);
                if (deno != null && deno.isValid() && deno.getPath() != null) {
                    command.add("--js-runtime");
                    command.add("deno:" + deno.getPath());
                } else {
                    scheduler.runSync(() -> sender.sendMessage(ChatColor.YELLOW + "Deno not available. yt-dlp may fail JS challenges; run /mtc deps status for details."));
                }
            }
            List<String> extraArgs = configuration.media_youtube_extra_args();
            if (extraArgs != null && !extraArgs.isEmpty()) {
                for (String arg : extraArgs) {
                    if (arg != null && !arg.isBlank()) {
                        command.add(arg);
                    }
                }
            }
            command.add("-g");
            command.add(url);
            Process process = new ProcessBuilder(command).start();
            process.waitFor();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            String errorOutput = new String(process.getErrorStream().readAllBytes()).trim();
            int exitCode = process.exitValue();
            lastResolverExitCode = exitCode;
            lastResolverError = errorOutput;
            if (exitCode != 0 || output.isEmpty()) {
                Bukkit.getLogger().warning("[MovieTheatreCore]: YouTube resolver exited with code " + exitCode + ". stderr: " + errorOutput);
                String message = "Resolver failed to return a direct URL.";
                if (!cookiesUsed) {
                    message = "YouTube blocked this request without cookies. Add youtube-cookies.txt or use a direct MP4/WEBM URL.";
                } else if (cookieStatus != null && cookieStatus.expired) {
                    message = "YouTube cookies are expired. Export a fresh youtube-cookies.txt and try again.";
                }
                String finalMessage = message;
                scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + finalMessage));
                return null;
            }
            for (String line : output.split("\n")) {
                String candidate = line.trim();
                if (!candidate.isEmpty()) {
                    scheduler.runSync(() -> sender.sendMessage(ChatColor.GREEN + "yt-dlp resolved a direct URL."));
                    return candidate;
                }
            }
            scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "Resolver failed to return a direct URL."));
            return null;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            lastResolverExitCode = null;
            lastResolverError = e.getMessage();
            scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "Resolver error: " + e.getMessage()));
            return null;
        }
    }

    private File resolveCookiesFile() {
        String cookiesPath = configuration.youtube_cookies_path();
        if (cookiesPath == null || cookiesPath.isBlank()) {
            File defaultFile = new File(plugin.getDataFolder(), "youtube-cookies.txt");
            return defaultFile.exists() ? defaultFile : null;
        }
        return new File(cookiesPath);
    }

    private CookieStatus evaluateCookies(File cookiesFile) {
        boolean expired = false;
        boolean hasEntries = false;
        long now = System.currentTimeMillis() / 1000L;
        try {
            List<String> lines = Files.readAllLines(cookiesFile.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length < 7) {
                    continue;
                }
                hasEntries = true;
                try {
                    long expiry = Long.parseLong(parts[4]);
                    if (expiry != 0 && expiry < now) {
                        expired = true;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            return new CookieStatus(false, true);
        }
        return new CookieStatus(hasEntries, expired);
    }

    private MediaHeadInfo fetchHeadInfo(String url) {
        int timeoutMs = configuration.media_download_timeout_seconds() * 1000;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("HEAD");
            int status = connection.getResponseCode();
            String contentType = connection.getContentType();
            connection.disconnect();
            if (status == HttpURLConnection.HTTP_BAD_METHOD) {
                return fetchRangeInfo(url, timeoutMs);
            }
            return new MediaHeadInfo(status, contentType);
        } catch (IOException e) {
            return null;
        }
    }

    private MediaHeadInfo fetchRangeInfo(String url, int timeoutMs) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Range", "bytes=0-0");
            int status = connection.getResponseCode();
            String contentType = connection.getContentType();
            try (var input = connection.getInputStream()) {
                // Intentionally ignore payload.
            } catch (IOException ignored) {
            }
            connection.disconnect();
            return new MediaHeadInfo(status, contentType);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isYoutubeUrl(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) {
                return false;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            return normalized.contains("youtube.com") || normalized.contains("youtu.be");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isAllowedUrl(String url) {
        String mode = configuration.sources_allowlist_mode();
        if (mode == null || mode.isBlank() || mode.equalsIgnoreCase("OFF")) {
            return true;
        }
        List<String> allowed = configuration.media_allowed_domains();
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        try {
            String host = new URI(url).getHost();
            if (host == null) {
                return false;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            for (String domain : allowed) {
                if (domain == null || domain.isBlank()) {
                    continue;
                }
                String allowedDomain = domain.toLowerCase(Locale.ROOT).trim();
                if (allowedDomain.startsWith("*.")) {
                    allowedDomain = allowedDomain.substring(2);
                }
                if (normalized.equals(allowedDomain) || normalized.endsWith("." + allowedDomain)) {
                    return true;
                }
            }
        } catch (URISyntaxException e) {
            return false;
        }
        return false;
    }

    public Integer getLastResolverExitCode() {
        return lastResolverExitCode;
    }

    public String getLastResolverError() {
        return lastResolverError;
    }

    private boolean isSafeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.contains("/") || value.contains("\\") || value.contains("..")) {
            return false;
        }
        return value.matches("[A-Za-z0-9_-]+");
    }

    private String normalizeIdentifier(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record CookieStatus(boolean hasEntries, boolean expired) {
    }

    private record MediaHeadInfo(int statusCode, String contentType) {
    }

 
}
