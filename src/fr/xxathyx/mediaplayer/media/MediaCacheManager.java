package fr.xxathyx.mediaplayer.media;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import fr.xxathyx.mediaplayer.configuration.Configuration;

public class MediaCacheManager {

    private final Configuration configuration;

    public MediaCacheManager(Configuration configuration) {
        this.configuration = configuration;
    }

    public File getCacheFolder() {
        return configuration.getMediaCacheFolder();
    }

    public File getCacheFile(MediaEntry entry) {
        return new File(getCacheFolder(), entry.getId() + "." + entry.getExtension());
    }

    public MediaEntry download(MediaEntry entry, long maxBytes, int timeoutSeconds) throws IOException {
        URL url = URI.create(entry.getUrl()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setInstanceFollowRedirects(true);

        int response = connection.getResponseCode();
        if (response < 200 || response >= 300) {
            throw new IOException("HTTP " + response);
        }

        String contentType = connection.getContentType();
        String extension = entry.getExtension();
        if (extension == null || extension.isEmpty()) {
            extension = sniffExtension(contentType, url.getPath());
        }

        MediaEntry updated = new MediaEntry(entry.getName(), entry.getUrl(), entry.getId(), extension, entry.isLibraryEntry());

        File tempFile = new File(getCacheFolder(), entry.getId() + "." + extension + ".tmp");
        File targetFile = new File(getCacheFolder(), entry.getId() + "." + extension);

        long total = 0;
        byte[] buffer = new byte[8192];

        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(tempFile)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Download exceeds max size.");
                }
                output.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }

        Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        updated.setSizeBytes(total);
        updated.setLastAccess(Instant.now().toEpochMilli());
        return updated;
    }

    public void touch(MediaEntry entry) {
        entry.setLastAccess(Instant.now().toEpochMilli());
    }

    public void evictIfNeeded(List<MediaEntry> entries, long maxBytes, Set<String> protectedIds) {
        long total = 0;
        for (MediaEntry entry : entries) {
            total += entry.getSizeBytes();
        }
        if (total <= maxBytes) {
            return;
        }

        entries.sort(Comparator.comparingLong(MediaEntry::getLastAccess));

        for (MediaEntry entry : entries) {
            if (total <= maxBytes) {
                break;
            }
            if (protectedIds.contains(entry.getId())) {
                continue;
            }
            File cacheFile = getCacheFile(entry);
            if (cacheFile.exists() && cacheFile.delete()) {
                total -= entry.getSizeBytes();
            }
        }
    }

    private String sniffExtension(String contentType, String path) {
        if (contentType != null) {
            if (contentType.contains("webm")) {
                return "webm";
            }
            if (contentType.contains("mp4")) {
                return "mp4";
            }
        }
        int dot = path.lastIndexOf('.');
        if (dot > -1 && dot + 1 < path.length()) {
            return path.substring(dot + 1);
        }
        return "mp4";
    }
}
