package com._650a.movietheatrecore.theatre;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.media.MediaPlayback;
import com._650a.movietheatrecore.media.MediaManager;
import com._650a.movietheatrecore.playback.PlaybackManager;
import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.screen.ScreenManager;
import com._650a.movietheatrecore.util.Scheduler;

public class TheatreManager {

    private final Main plugin;
    private final Configuration configuration;
    private final ScreenManager screenManager;
    private final MediaManager mediaManager;
    private final PlaybackManager playbackManager;
    private final Scheduler scheduler;
    private final TheatreStorage storage;
    private final Map<UUID, TheatreRoom> rooms = new HashMap<>();
    private final Map<UUID, List<ShowScheduleEntry>> schedules = new HashMap<>();
    private final Map<UUID, ShowInstance> activeShows = new HashMap<>();
    private BukkitTask scheduleTask;

    public TheatreManager(Main plugin, ScreenManager screenManager, MediaManager mediaManager, PlaybackManager playbackManager) {
        this.plugin = plugin;
        this.configuration = new Configuration();
        this.screenManager = screenManager;
        this.mediaManager = mediaManager;
        this.playbackManager = playbackManager;
        this.scheduler = new Scheduler(plugin);
        this.storage = new TheatreStorage(plugin, configuration);
    }

    public void load() {
        rooms.clear();
        schedules.clear();
        rooms.putAll(storage.loadRooms());
        schedules.putAll(storage.loadSchedules());
        startScheduler();
    }

    public void reload() {
        stopScheduler();
        stopAllShows();
        load();
    }

    public void shutdown() {
        stopScheduler();
        stopAllShows();
    }

    public Map<UUID, TheatreRoom> getRooms() {
        return rooms;
    }

    public Map<UUID, ShowInstance> getActiveShows() {
        return activeShows;
    }

    public TheatreRoom getRoom(String name) {
        if (name == null) {
            return null;
        }
        for (TheatreRoom room : rooms.values()) {
            if (room.getName().equalsIgnoreCase(name)) {
                return room;
            }
        }
        return null;
    }

    public boolean createRoom(CommandSender sender, String name, List<String> screenNames) {
        if (getRoom(name) != null) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "Room already exists: " + name);
            }
            return false;
        }
        AudioZone zone = null;
        if (sender instanceof Player player) {
            zone = AudioZone.fromLocation(player.getLocation(), configuration.theatre_default_zone_radius());
        }
        TheatreRoom room = new TheatreRoom(UUID.randomUUID(), name, screenNames, zone, new ArrayList<>());
        rooms.put(room.getId(), room);
        storage.saveRooms(rooms.values());
        if (sender != null) {
            sender.sendMessage(ChatColor.GREEN + "Room created: " + room.getName());
        }
        log("Created room " + room.getName());
        return true;
    }

    public boolean deleteRoom(CommandSender sender, String name) {
        TheatreRoom room = getRoom(name);
        if (room == null) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "Unknown room: " + name);
            }
            return false;
        }
        stopShow(room);
        rooms.remove(room.getId());
        schedules.remove(room.getId());
        storage.saveRooms(rooms.values());
        storage.saveSchedules(schedules);
        if (sender != null) {
            sender.sendMessage(ChatColor.GREEN + "Room deleted: " + room.getName());
        }
        log("Deleted room " + room.getName());
        return true;
    }

    public void playRoom(CommandSender sender, TheatreRoom room, String mediaId) {
        if (!configuration.theatre_enabled()) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "Theatre mode is disabled in configuration.");
            }
            return;
        }
        if (room == null) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "Unknown room.");
            }
            return;
        }
        List<Screen> screens = room.resolveScreens(screenManager);
        if (screens.isEmpty()) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "Room has no valid screens assigned.");
            }
            return;
        }
        int maxShows = Math.min(configuration.theatre_max_shows(), configuration.maximum_playing_videos());
        if (activeShows.size() >= maxShows) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "Maximum concurrent shows reached (" + maxShows + ").");
            }
            log("Show start blocked: max shows reached (" + maxShows + ")");
            return;
        }
        stopShow(room);

        mediaManager.prepareMediaPlayback(mediaId, playback -> {
            AudioZone zone = resolveAudioZone(room, screens);
            ShowInstance show = new ShowInstance(plugin, playbackManager, room, mediaId, zone, () -> activeShows.remove(room.getId()));
            activeShows.put(room.getId(), show);
            show.start(playback, screens);
            if (sender != null) {
                sender.sendMessage(ChatColor.GREEN + "Started show in room " + room.getName() + " (" + mediaId + ").");
            }
            log("Started show " + mediaId + " in room " + room.getName());
        }, error -> {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + error);
            } else {
                log("Failed to start scheduled show for room " + room.getName() + ": " + error);
            }
        });
    }

    public void stopShow(TheatreRoom room) {
        if (room == null) {
            return;
        }
        ShowInstance show = activeShows.get(room.getId());
        if (show != null) {
            show.stop();
            log("Stopped show in room " + room.getName());
        }
    }

    public ShowScheduleEntry addSchedule(TheatreRoom room, LocalTime time, ShowRepeat repeat, String mediaId) {
        if (room == null || time == null || mediaId == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(time.getHour()).withMinute(time.getMinute()).withSecond(0).withNano(0);
        if (nextRun.isBefore(now)) {
            if (repeat == ShowRepeat.WEEKLY) {
                nextRun = nextRun.plusWeeks(1);
            } else {
                nextRun = nextRun.plusDays(1);
            }
        }
        ShowScheduleEntry entry = new ShowScheduleEntry(UUID.randomUUID(), mediaId, nextRun, repeat, true);
        schedules.computeIfAbsent(room.getId(), unused -> new ArrayList<>()).add(entry);
        storage.saveSchedules(schedules);
        return entry;
    }

    public boolean removeSchedule(TheatreRoom room, String token) {
        if (room == null || token == null) {
            return false;
        }
        List<ShowScheduleEntry> entries = schedules.get(room.getId());
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        ShowScheduleEntry target = null;
        try {
            int index = Integer.parseInt(token);
            int zeroIndex = index - 1;
            if (zeroIndex >= 0 && zeroIndex < entries.size()) {
                target = entries.get(zeroIndex);
            }
        } catch (NumberFormatException ignored) {
        }
        if (target == null) {
            try {
                UUID id = UUID.fromString(token);
                for (ShowScheduleEntry entry : entries) {
                    if (entry.getId().equals(id)) {
                        target = entry;
                        break;
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (target == null) {
            return false;
        }
        entries.remove(target);
        storage.saveSchedules(schedules);
        return true;
    }

    public List<ShowScheduleEntry> getSchedules(TheatreRoom room) {
        if (room == null) {
            return List.of();
        }
        return schedules.getOrDefault(room.getId(), List.of());
    }

    public List<String> buildDoctorReport() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + "Rooms: " + rooms.size());
        lines.add(ChatColor.GRAY + "Active shows: " + activeShows.size());
        int scheduleCount = schedules.values().stream().mapToInt(List::size).sum();
        lines.add(ChatColor.GRAY + "Schedules: " + scheduleCount);
        lines.add(ChatColor.GRAY + "Theatre enabled: " + yesNo(configuration.theatre_enabled()));
        lines.add(ChatColor.GRAY + "FFmpeg available: " + yesNo(plugin.getFfmpeg().isAvailable()));
        lines.add(ChatColor.GRAY + "FFprobe available: " + yesNo(plugin.getFfprobe().isAvailable()));
        if (!configuration.theatre_enabled()) {
            lines.add(ChatColor.YELLOW + "Warning: theatre.enabled is false.");
        }
        for (TheatreRoom room : rooms.values()) {
            if (room.resolveScreens(screenManager).isEmpty()) {
                lines.add(ChatColor.YELLOW + "Warning: room " + room.getName() + " has no valid screens.");
            }
        }
        return lines;
    }

    private void startScheduler() {
        stopScheduler();
        long intervalSeconds = Math.max(5, configuration.theatre_schedule_check_interval_seconds());
        scheduleTask = scheduler.runSyncRepeating(this::checkSchedules, 20L, intervalSeconds * 20L);
    }

    private void stopScheduler() {
        if (scheduleTask != null) {
            scheduleTask.cancel();
            scheduleTask = null;
        }
    }

    private void checkSchedules() {
        if (!configuration.theatre_enabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<UUID, List<ShowScheduleEntry>> entry : schedules.entrySet()) {
            TheatreRoom room = rooms.get(entry.getKey());
            if (room == null) {
                continue;
            }
            for (ShowScheduleEntry schedule : new ArrayList<>(entry.getValue())) {
                if (!schedule.isDue(now)) {
                    continue;
                }
                if (activeShows.containsKey(room.getId())) {
                    continue;
                }
                playRoom(null, room, schedule.getMediaId());
                schedule.markTriggered(now);
                storage.saveSchedules(schedules);
            }
        }
    }

    private AudioZone resolveAudioZone(TheatreRoom room, List<Screen> screens) {
        AudioZone zone = room.getAudioZone();
        if (zone != null && zone.isValid() && zone.toLocation() != null) {
            return zone;
        }
        if (screens == null || screens.isEmpty()) {
            return zone;
        }
        Screen screen = screens.get(0);
        return AudioZone.fromLocation(screen.getAudioSpeakerLocation(), configuration.theatre_default_zone_radius());
    }

    private void stopAllShows() {
        for (ShowInstance show : new ArrayList<>(activeShows.values())) {
            show.stop();
        }
        activeShows.clear();
    }

    private void log(String message) {
        plugin.getLogger().info("[MovieTheatreCore:Theatre]: " + message);
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
