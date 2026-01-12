package fr.xxathyx.mediaplayer.media;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.audio.AudioPackManager;
import fr.xxathyx.mediaplayer.audio.AudioTrack;
import fr.xxathyx.mediaplayer.configuration.Configuration;
import fr.xxathyx.mediaplayer.dependency.DependencyManager;
import fr.xxathyx.mediaplayer.playback.PlaybackOptions;
import fr.xxathyx.mediaplayer.screen.Screen;
import fr.xxathyx.mediaplayer.tasks.TaskAsyncLoadConfigurations;
import fr.xxathyx.mediaplayer.util.Scheduler;
import fr.xxathyx.mediaplayer.video.Video;

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

    public void addMedia(CommandSender sender, String name, String url) {
        if (library.getEntry(name) != null) {
            sender.sendMessage(ChatColor.RED + "Media with that name already exists.");
            return;
        }
        String resolved = resolveUrl(sender, url);
        if (resolved == null) {
            return;
        }
        if (!isAllowedUrl(resolved)) {
            sender.sendMessage(ChatColor.RED + "URL not allowed by sources.allowlist-mode (STRICT).");
            return;
        }
        if (!plugin.getFfprobe().isAvailable()) {
            sender.sendMessage(configuration.libraries_not_installed());
            return;
        }

        scheduler.runAsync(() -> {
            try {
                MediaEntry entry = downloadEntry(name, resolved, true);
                File videoFile = ensureVideoFile(entry);
                File configFile = getVideoConfigFile(entry);
                Video video = new Video(configFile);
                if (!configFile.exists()) {
                    video.createConfiguration(videoFile);
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

    public void playMedia(CommandSender sender, Screen screen, String name, boolean noAudio) {
        MediaEntry entry = library.getEntry(name);
        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "Unknown media: " + name);
            return;
        }
        playEntry(sender, screen, entry, noAudio);
    }

    public void playUrl(CommandSender sender, Screen screen, String url, boolean noAudio) {
        String resolved = resolveUrl(sender, url);
        if (resolved == null) {
            return;
        }
        if (!isAllowedUrl(resolved)) {
            sender.sendMessage(ChatColor.RED + "URL not allowed by sources.allowlist-mode (STRICT).");
            return;
        }
        String urlHash = Integer.toHexString(resolved.hashCode());
        MediaEntry entry = library.getCachedByUrl(urlHash);
        if (entry != null && cacheManager.getCacheFile(entry).exists()) {
            playEntry(sender, screen, entry, noAudio);
            return;
        }

        scheduler.runAsync(() -> {
            try {
                String tempName = "url-" + urlHash;
                MediaEntry downloaded = downloadEntry(tempName, resolved, false);
                library.addUrlCache(urlHash, downloaded);
                ensureVideoFile(downloaded);
                reloadVideos();
                scheduler.runSync(() -> playEntry(sender, screen, downloaded, noAudio));
            } catch (IOException e) {
                scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "Failed to download URL: " + e.getMessage()));
            }
        });
    }

    private MediaEntry downloadEntry(String name, String url, boolean libraryEntry) throws IOException {
        String extension = FilenameUtils.getExtension(url);
        MediaEntry entry = new MediaEntry(name, url, UUID.randomUUID().toString(), extension, libraryEntry);
        long maxBytes = configuration.media_max_download_mb() * 1024L * 1024L;
        MediaEntry downloaded = cacheManager.download(entry, maxBytes, configuration.media_download_timeout_seconds());
        cacheManager.touch(downloaded);
        library.save();
        cacheManager.evictIfNeeded(library.listAllCached(), configuration.media_cache_max_gb() * 1024L * 1024L * 1024L, Set.of(downloaded.getId()));
        return downloaded;
    }

    private void playEntry(CommandSender sender, Screen screen, MediaEntry entry, boolean noAudio) {
        scheduler.runAsync(() -> {
            try {
                cacheManager.touch(entry);
                library.save();
                File videoFile = ensureVideoFile(entry);
                File configFile = getVideoConfigFile(entry);
                Video video = new Video(configFile);
                if (!configFile.exists()) {
                    if (!plugin.getFfprobe().isAvailable()) {
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
                AudioTrack track = null;
                boolean allowAudio = configuration.audio_enabled() && !noAudio;
                if (allowAudio) {
                    track = audioPackManager.prepare(entry, videoFile);
                    library.save();
                    if (track == null) {
                        allowAudio = false;
                    }
                }
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
        File temp = new File(configuration.getVideosFolder(), entry.getName() + "." + entry.getExtension() + ".tmp");
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
        File packFile = new File(configuration.getResourcePackFolder(), entry.getId() + ".zip");
        if (packFile.exists()) {
            packFile.delete();
        }
        File packFolder = new File(configuration.getResourcePackFolder(), entry.getId());
        if (packFolder.exists()) {
            FileUtils.deleteDirectory(packFolder);
        }
        File audioFolder = new File(configuration.getAudioChunksFolder(), entry.getId());
        if (audioFolder.exists()) {
            FileUtils.deleteDirectory(audioFolder);
        }
    }

    private void reloadVideos() {
        scheduler.runSync(() -> new TaskAsyncLoadConfigurations().runTaskAsynchronously(plugin));
    }

    private String resolveUrl(CommandSender sender, String url) {
        if (!isYoutubeUrl(url)) {
            return url;
        }
        DependencyManager dependencyManager = plugin.getDependencyManager();
        DependencyManager.ResolvedBinary resolver = dependencyManager.resolveBinary(DependencyManager.BinaryType.YT_DLP, true);
        if (resolver == null || !resolver.isValid() || resolver.getStagedPath() == null) {
            sender.sendMessage(ChatColor.RED + "YouTube resolver not available. Install yt-dlp or enable auto-update-libraries, then run /mp diagnose.");
            lastResolverExitCode = null;
            lastResolverError = resolver == null ? "yt-dlp not resolved" : resolver.getError();
            return null;
        }
        try {
            List<String> command = new java.util.ArrayList<>();
            command.add(resolver.getStagedPath().toString());
            String cookiesPath = configuration.media_youtube_cookies_path();
            if (cookiesPath == null || cookiesPath.isBlank()) {
                sender.sendMessage(ChatColor.YELLOW + "No cookies file configured. Set sources.youtube-cookies-path to reduce YouTube bot checks.");
            } else {
                File cookiesFile = new File(cookiesPath);
                if (cookiesFile.exists() && cookiesFile.canRead()) {
                    command.add("--cookies");
                    command.add(cookiesFile.getAbsolutePath());
                } else {
                    sender.sendMessage(ChatColor.RED + "Cookies file missing or unreadable: " + cookiesFile.getPath() + ". Add cookies to avoid YouTube bot checks.");
                }
            }
            DependencyManager.ResolvedBinary deno = dependencyManager.resolveBinary(DependencyManager.BinaryType.DENO, false);
            if (deno != null && deno.isValid() && deno.getStagedPath() != null) {
                command.add("--js-runtime");
                command.add("deno:" + deno.getStagedPath());
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Deno not available. yt-dlp may fail JS challenges; run /mp diagnose for details.");
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
                Bukkit.getLogger().warning("[MediaPlayer]: YouTube resolver exited with code " + exitCode + ". stderr: " + errorOutput);
                sender.sendMessage(ChatColor.RED + "Resolver failed to return a direct URL.");
                return null;
            }
            for (String line : output.split("\n")) {
                String candidate = line.trim();
                if (!candidate.isEmpty()) {
                    sender.sendMessage(ChatColor.GREEN + "yt-dlp resolved a direct URL.");
                    return candidate;
                }
            }
            sender.sendMessage(ChatColor.RED + "Resolver failed to return a direct URL.");
            return null;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            lastResolverExitCode = null;
            lastResolverError = e.getMessage();
            sender.sendMessage(ChatColor.RED + "Resolver error: " + e.getMessage());
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

 
}
