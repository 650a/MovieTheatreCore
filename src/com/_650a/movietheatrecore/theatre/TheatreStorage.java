package com._650a.movietheatrecore.theatre;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;

public class TheatreStorage {

    private final Main plugin;
    private final Configuration configuration;
    private final File roomsFile;
    private final File schedulesFile;

    public TheatreStorage(Main plugin, Configuration configuration) {
        this.plugin = plugin;
        this.configuration = configuration;
        File folder = configuration.getTheatreFolder();
        this.roomsFile = new File(folder, "rooms.yml");
        this.schedulesFile = new File(folder, "schedules.yml");
    }

    public Map<UUID, TheatreRoom> loadRooms() {
        Map<UUID, TheatreRoom> rooms = new HashMap<>();
        if (!roomsFile.exists()) {
            return rooms;
        }
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(roomsFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("[MovieTheatreCore:Theatre]: Failed to load rooms.yml: " + e.getMessage());
            return rooms;
        }
        ConfigurationSection roomsSection = config.getConfigurationSection("rooms");
        if (roomsSection == null) {
            return rooms;
        }
        for (String key : roomsSection.getKeys(false)) {
            UUID roomId;
            try {
                roomId = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection roomSection = roomsSection.getConfigurationSection(key);
            if (roomSection == null) {
                continue;
            }
            String name = roomSection.getString("name", key);
            List<String> screens = roomSection.getStringList("screens");
            AudioZone zone = null;
            if (roomSection.contains("audio-zone.world")) {
                String world = roomSection.getString("audio-zone.world");
                double x = roomSection.getDouble("audio-zone.x");
                double y = roomSection.getDouble("audio-zone.y");
                double z = roomSection.getDouble("audio-zone.z");
                int radius = roomSection.getInt("audio-zone.radius", configuration.theatre_default_zone_radius());
                zone = new AudioZone(world, x, y, z, radius);
            }
            List<TheatreSeat> seats = new ArrayList<>();
            for (String seatValue : roomSection.getStringList("seats")) {
                TheatreSeat seat = TheatreSeat.parse(seatValue);
                if (seat != null) {
                    seats.add(seat);
                }
            }
            rooms.put(roomId, new TheatreRoom(roomId, name, screens, zone, seats));
        }
        return rooms;
    }

    public void saveRooms(Iterable<TheatreRoom> rooms) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection roomsSection = config.createSection("rooms");
        for (TheatreRoom room : rooms) {
            ConfigurationSection roomSection = roomsSection.createSection(room.getId().toString());
            roomSection.set("name", room.getName());
            roomSection.set("screens", new ArrayList<>(room.getScreens()));
            AudioZone zone = room.getAudioZone();
            if (zone != null) {
                roomSection.set("audio-zone.world", zone.getWorld());
                roomSection.set("audio-zone.x", zone.getX());
                roomSection.set("audio-zone.y", zone.getY());
                roomSection.set("audio-zone.z", zone.getZ());
                roomSection.set("audio-zone.radius", zone.getRadius());
            }
            List<String> seats = new ArrayList<>();
            for (TheatreSeat seat : room.getSeats()) {
                seats.add(seat.toString());
            }
            roomSection.set("seats", seats);
        }
        try {
            config.save(roomsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[MovieTheatreCore:Theatre]: Failed to save rooms.yml: " + e.getMessage());
        }
    }

    public Map<UUID, List<ShowScheduleEntry>> loadSchedules() {
        Map<UUID, List<ShowScheduleEntry>> schedules = new HashMap<>();
        if (!schedulesFile.exists()) {
            return schedules;
        }
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(schedulesFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("[MovieTheatreCore:Theatre]: Failed to load schedules.yml: " + e.getMessage());
            return schedules;
        }
        ConfigurationSection roomsSection = config.getConfigurationSection("rooms");
        if (roomsSection == null) {
            return schedules;
        }
        for (String roomKey : roomsSection.getKeys(false)) {
            UUID roomId;
            try {
                roomId = UUID.fromString(roomKey);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection roomSection = roomsSection.getConfigurationSection(roomKey);
            if (roomSection == null) {
                continue;
            }
            ConfigurationSection entriesSection = roomSection.getConfigurationSection("entries");
            if (entriesSection == null) {
                continue;
            }
            List<ShowScheduleEntry> entries = new ArrayList<>();
            for (String entryKey : entriesSection.getKeys(false)) {
                UUID entryId;
                try {
                    entryId = UUID.fromString(entryKey);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                ConfigurationSection entrySection = entriesSection.getConfigurationSection(entryKey);
                if (entrySection == null) {
                    continue;
                }
                String mediaId = entrySection.getString("media-id");
                ShowRepeat repeat = ShowRepeat.fromString(entrySection.getString("repeat"));
                boolean enabled = entrySection.getBoolean("enabled", true);
                LocalDateTime nextRun = parseDate(entrySection.getString("next-run"));
                ShowScheduleEntry entry = new ShowScheduleEntry(entryId, mediaId, nextRun, repeat, enabled);
                LocalDateTime lastTriggered = parseDate(entrySection.getString("last-triggered"));
                entry.setLastTriggered(lastTriggered);
                entries.add(entry);
            }
            schedules.put(roomId, entries);
        }
        return schedules;
    }

    public void saveSchedules(Map<UUID, List<ShowScheduleEntry>> schedules) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection roomsSection = config.createSection("rooms");
        for (Map.Entry<UUID, List<ShowScheduleEntry>> entry : schedules.entrySet()) {
            ConfigurationSection roomSection = roomsSection.createSection(entry.getKey().toString());
            ConfigurationSection entriesSection = roomSection.createSection("entries");
            for (ShowScheduleEntry schedule : entry.getValue()) {
                ConfigurationSection scheduleSection = entriesSection.createSection(schedule.getId().toString());
                scheduleSection.set("media-id", schedule.getMediaId());
                scheduleSection.set("repeat", schedule.getRepeat().name());
                scheduleSection.set("enabled", schedule.isEnabled());
                scheduleSection.set("next-run", formatDate(schedule.getNextRun()));
                scheduleSection.set("last-triggered", formatDate(schedule.getLastTriggered()));
            }
        }
        try {
            config.save(schedulesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[MovieTheatreCore:Theatre]: Failed to save schedules.yml: " + e.getMessage());
        }
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, ShowScheduleEntry.FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(ShowScheduleEntry.FORMATTER);
    }
}
