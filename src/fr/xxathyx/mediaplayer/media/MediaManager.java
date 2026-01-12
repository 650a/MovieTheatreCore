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
            sender.sendMessage(ChatColor.RED + "URL not allowed by media.allowed-domains.");
            return;
        }
        if (!plugin.getFfprobe().isAvailable()) {
            sender.sendMessage(configuration.libraries_not_installed());
            return;
        }

        scheduler.runAsync(() -> {
            try {
                MediaEntry entry = downloadEntry(name, resolved, true);
                ensureVideoFile(entry);
                library.addEntry(entry);
                reloadVideos();
                scheduler.runSync(() -> sender.sendMessage(ChatColor.GREEN + "Media added: " + entry.getName()));
            } catch (IOException e) {
                scheduler.runSync(() -> sender.sendMessage(ChatColor.RED + "Failed to download media: " + e.getMessage()));
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
            sender.sendMessage(ChatColor.RED + "URL not allowed by media.allowed-domains.");
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
        String resolverPath = configuration.media_youtube_resolver_path();
        if (resolverPath == null || resolverPath.isBlank()) {
            sender.sendMessage(ChatColor.RED + "YouTube URLs require a resolver. Configure media.youtube-resolver-path or use a direct URL.");
            return null;
        }
        File resolver = new File(resolverPath);
        if (!resolver.exists()) {
            sender.sendMessage(ChatColor.RED + "YouTube resolver not found. Configure media.youtube-resolver-path or use a direct URL.");
            return null;
        }
        try {
            Process process = new ProcessBuilder(resolver.getAbsolutePath(), "-f", "best", "-g", url).start();
            process.waitFor();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (output.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Resolver failed to return a direct URL.");
                return null;
            }
            return output.split("\n")[0].trim();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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
                String allowedDomain = domain.toLowerCase(Locale.ROOT);
                if (normalized.equals(allowedDomain) || normalized.endsWith("." + allowedDomain)) {
                    return true;
                }
            }
        } catch (URISyntaxException e) {
            return false;
        }
        return false;
    }
}
