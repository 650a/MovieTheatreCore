package com._650a.movietheatrecore.media;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com._650a.movietheatrecore.Main;

public class MediaLibrary {

    private final Main plugin;
    private final File libraryFile;
    private final Map<String, MediaEntry> entries = new LinkedHashMap<>();
    private final Map<String, MediaEntry> urlCache = new LinkedHashMap<>();

    public MediaLibrary(Main plugin) {
        this.plugin = plugin;
        this.libraryFile = new File(plugin.getDataFolder(), "media.yml");
        load();
    }

    public void load() {
        entries.clear();
        urlCache.clear();

        if (!libraryFile.exists()) {
            save();
            return;
        }

        FileConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(libraryFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            return;
        }

        loadSection(configuration, "media.entries", true, entries);
        loadSection(configuration, "media.url-cache", false, urlCache);
    }

    private void loadSection(FileConfiguration configuration, String path, boolean library, Map<String, MediaEntry> target) {
        if (configuration.getConfigurationSection(path) == null) {
            return;
        }
        for (String key : configuration.getConfigurationSection(path).getKeys(false)) {
            String base = path + "." + key;
            String id = configuration.getString(base + ".id");
            String url = configuration.getString(base + ".url");
            String extension = configuration.getString(base + ".extension", "mp4");
            MediaEntry entry = new MediaEntry(key, url, id, extension, library);
            entry.setSizeBytes(configuration.getLong(base + ".size-bytes"));
            entry.setLastAccess(configuration.getLong(base + ".last-access"));
            entry.setAudioSha1(configuration.getString(base + ".audio-sha1"));
            entry.setAudioChunks(configuration.getInt(base + ".audio-chunks"));
            target.put(key, entry);
        }
    }

    public void save() {
        FileConfiguration configuration = new YamlConfiguration();

        storeSection(configuration, "media.entries", entries);
        storeSection(configuration, "media.url-cache", urlCache);

        try {
            configuration.save(libraryFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void storeSection(FileConfiguration configuration, String path, Map<String, MediaEntry> source) {
        for (Map.Entry<String, MediaEntry> entry : source.entrySet()) {
            String base = path + "." + entry.getKey();
            MediaEntry media = entry.getValue();
            configuration.set(base + ".id", media.getId());
            configuration.set(base + ".url", media.getUrl());
            configuration.set(base + ".extension", media.getExtension());
            configuration.set(base + ".size-bytes", media.getSizeBytes());
            configuration.set(base + ".last-access", media.getLastAccess());
            configuration.set(base + ".audio-sha1", media.getAudioSha1());
            configuration.set(base + ".audio-chunks", media.getAudioChunks());
        }
    }

    public MediaEntry getEntry(String name) {
        return entries.get(name.toLowerCase());
    }

    public MediaEntry getCachedByUrl(String urlHash) {
        return urlCache.get(urlHash);
    }

    public void addEntry(MediaEntry entry) {
        entries.put(entry.getName().toLowerCase(), entry);
        save();
    }

    public void addUrlCache(String urlHash, MediaEntry entry) {
        urlCache.put(urlHash, entry);
        save();
    }

    public void removeEntry(String name) {
        entries.remove(name.toLowerCase());
        save();
    }

    public void removeUrlCache(String urlHash) {
        urlCache.remove(urlHash);
        save();
    }

    public boolean isIdInUse(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        for (MediaEntry entry : listAllCached()) {
            if (id.equalsIgnoreCase(entry.getId())) {
                return true;
            }
        }
        return false;
    }

    public List<MediaEntry> listEntries() {
        return new ArrayList<>(entries.values());
    }

    public List<MediaEntry> listAllCached() {
        List<MediaEntry> all = new ArrayList<>(entries.values());
        all.addAll(urlCache.values());
        return all;
    }
}
