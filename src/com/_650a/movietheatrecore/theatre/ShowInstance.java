package com._650a.movietheatrecore.theatre;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.media.MediaPlayback;
import com._650a.movietheatrecore.playback.PlaybackManager;
import com._650a.movietheatrecore.playback.PlaybackOptions;
import com._650a.movietheatrecore.playback.PlaybackSession;
import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.screen.ScreenState;
import com._650a.movietheatrecore.util.Scheduler;

public class ShowInstance {

    private final Main plugin;
    private final Configuration configuration;
    private final PlaybackManager playbackManager;
    private final Scheduler scheduler;
    private final TheatreRoom room;
    private final String mediaId;
    private final AudioZone audioZone;
    private final Set<UUID> audience = ConcurrentHashMap.newKeySet();
    private final List<PlaybackSession> sessions = new ArrayList<>();
    private final Runnable onStopped;

    private ShowState state = ShowState.IDLE;
    private LocalDateTime startTime;
    private BukkitTask audienceTask;
    private BukkitTask tickTask;

    public ShowInstance(Main plugin, PlaybackManager playbackManager, TheatreRoom room, String mediaId, AudioZone audioZone, Runnable onStopped) {
        this.plugin = plugin;
        this.configuration = new Configuration();
        this.playbackManager = playbackManager;
        this.scheduler = new Scheduler(plugin);
        this.room = room;
        this.mediaId = mediaId;
        this.audioZone = audioZone;
        this.onStopped = onStopped;
    }

    public String getMediaId() {
        return mediaId;
    }

    public TheatreRoom getRoom() {
        return room;
    }

    public ShowState getState() {
        return state;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public Set<UUID> getAudience() {
        return new HashSet<>(audience);
    }

    public void start(MediaPlayback playback, List<Screen> screens) {
        if (state == ShowState.PLAYING || state == ShowState.STARTING) {
            return;
        }
        state = ShowState.STARTING;
        startTime = LocalDateTime.now();

        PlaybackOptions baseOptions = playback.getOptions();
        boolean audioAssigned = false;

        for (Screen screen : screens) {
            PlaybackOptions options = baseOptions;
            if (audioAssigned) {
                options = new PlaybackOptions(false, baseOptions.mediaEntry(), null);
            }
            PlaybackSession session = playbackManager.start(screen, playback.getVideo(), options);
            if (!audioAssigned && baseOptions.allowAudio()) {
                audioAssigned = true;
                session.setAudioAudienceFilter(player -> audience.contains(player.getUniqueId()));
            }
            sessions.add(session);
        }

        int audienceInterval = Math.max(1, configuration.theatre_audience_check_interval());
        audienceTask = scheduler.runSyncRepeating(this::updateAudience, 0L, audienceInterval);

        int tickInterval = Math.max(1, configuration.theatre_tick_interval());
        tickTask = scheduler.runSyncRepeating(this::tick, 0L, tickInterval);

        state = ShowState.PLAYING;
    }

    public void stop() {
        if (state == ShowState.STOPPING || state == ShowState.STOPPED) {
            return;
        }
        state = ShowState.STOPPING;
        if (audienceTask != null) {
            audienceTask.cancel();
            audienceTask = null;
        }
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (PlaybackSession session : sessions) {
            Screen screen = session.getScreen();
            playbackManager.stop(screen, ScreenState.IDLE, true);
        }
        sessions.clear();
        audience.clear();
        state = ShowState.STOPPED;
        if (onStopped != null) {
            onStopped.run();
        }
    }

    private void tick() {
        if (state != ShowState.PLAYING) {
            return;
        }
        boolean anyActive = false;
        for (PlaybackSession session : sessions) {
            Screen screen = session.getScreen();
            if (playbackManager.getSession(screen) != null) {
                anyActive = true;
                break;
            }
        }
        if (!anyActive) {
            stop();
        }
    }

    private void updateAudience() {
        Location location = audioZone == null ? null : audioZone.toLocation();
        if (location == null || location.getWorld() == null) {
            audience.clear();
            return;
        }
        int radius = Math.max(1, audioZone.getRadius());
        Set<UUID> seen = new HashSet<>();
        for (Entity entity : getNearbyEntities(location, radius)) {
            if (entity.getType() == EntityType.PLAYER) {
                Player player = (Player) entity;
                if (player.isOnline()) {
                    seen.add(player.getUniqueId());
                }
            }
        }
        audience.retainAll(seen);
        audience.addAll(seen);
    }

    private List<Entity> getNearbyEntities(Location location, int radius) {
        if (plugin.isOld()) {
            int chunkRadius = radius < 16 ? 1 : (radius - (radius % 16)) / 16;
            List<Entity> radiusEntities = new ArrayList<>();

            for (int chunkX = 0 - chunkRadius; chunkX <= chunkRadius; chunkX++) {
                for (int chunkZ = 0 - chunkRadius; chunkZ <= chunkRadius; chunkZ++) {
                    int x = (int) location.getX(), y = (int) location.getY(), z = (int) location.getZ();
                    for (Entity entity : new Location(location.getWorld(), x + (chunkX * 16), y, z + (chunkZ * 16)).getChunk().getEntities()) {
                        if (entity.getLocation().distance(location) <= radius && entity.getLocation().getBlock() != location.getBlock()) {
                            radiusEntities.add(entity);
                        }
                    }
                }
            }
            return radiusEntities;
        }
        return new ArrayList<>(location.getWorld().getNearbyEntities(location, radius, radius, radius));
    }
}
