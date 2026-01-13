package com._650a.movietheatrecore.playback;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.audio.AudioPlayback;
import com._650a.movietheatrecore.audio.AudioTrack;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.items.ItemStacks;
import com._650a.movietheatrecore.map.colors.MapColorPalette;
import com._650a.movietheatrecore.render.FrameScaler;
import com._650a.movietheatrecore.render.MapTileSplitter;
import com._650a.movietheatrecore.render.ScalingMode;
import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.screen.ScreenState;
import com._650a.movietheatrecore.server.Server;
import com._650a.movietheatrecore.util.Scheduler;
import com._650a.movietheatrecore.video.Video;
import com._650a.movietheatrecore.video.data.VideoData;

public class PlaybackSession {

    private final Main plugin;
    private final Configuration configuration;
    private final Screen screen;
    private final Video video;
    private final PlaybackManager manager;
    private final PlaybackOptions options;
    private final UUID sessionId;
    private final Scheduler scheduler;
    private final FrameScaler scaler = new FrameScaler();
    private final ItemStacks itemStacks = new ItemStacks();
    private final AtomicBoolean rendering = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final Set<BukkitTask> renderTasks = ConcurrentHashMap.newKeySet();

    private final Set<UUID> viewers = new HashSet<>();
    private final Set<UUID> audioListeners = new HashSet<>();

    private BukkitTask tickTask;
    private java.util.function.Predicate<Player> audioAudienceFilter;
    private int audioUpdateCounter = 0;
    private boolean paused = false;
    private volatile boolean active = true;
    private int frameIndex = 0;
    private long lastFrameNanos = 0L;
    private long frameDurationNanos;
    private PlaybackState state = PlaybackState.IDLE;
    private AudioPlayback audioPlayback;

    private Server resourcePackServer;
    private AudioTrack audioTrack;

    public PlaybackSession(Main plugin, Screen screen, Video video, PlaybackManager manager, PlaybackOptions options) {
        this.plugin = plugin;
        this.configuration = new Configuration();
        this.screen = screen;
        this.video = video;
        this.manager = manager;
        this.options = options == null ? PlaybackOptions.defaultOptions() : options;
        this.sessionId = UUID.randomUUID();
        this.frameDurationNanos = (long) (1_000_000_000L / Math.max(1.0, video.getFrameRate()));
        this.scheduler = new Scheduler(plugin);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Screen getScreen() {
        return screen;
    }

    public Video getVideo() {
        return video;
    }

    public void setAudioAudienceFilter(java.util.function.Predicate<Player> filter) {
        this.audioAudienceFilter = filter;
    }

    public void start() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        state = PlaybackState.PREPARING;
        active = true;
        setupResourcePack();
        lastFrameNanos = System.nanoTime();
        state = PlaybackState.PLAYING;

        tickTask = scheduler.runSyncRepeating(this::tick, 0L, 1L);

        if (audioTrack != null) {
            audioPlayback = new AudioPlayback(scheduler, audioTrack, this::getAudioListenerSnapshot, this::getAudioSpeakerLocation, () -> active);
            audioPlayback.start();
        }
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public void stop(boolean showThumbnail) {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        state = PlaybackState.STOPPING;
        active = false;
        paused = false;
        rendering.set(false);

        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        for (BukkitTask task : renderTasks) {
            task.cancel();
        }
        renderTasks.clear();

        if (resourcePackServer != null) {
            resourcePackServer.stop();
            resourcePackServer = null;
        }

        if (audioPlayback != null) {
            audioPlayback.stop();
            audioPlayback = null;
        }

        if (video.isAudioEnabled() && options.allowAudio()) {
            for (UUID uuid : audioListeners) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    for (int i = 0; i < video.getAudioChannels(); i++) {
                        plugin.getAudioUtil().stopAudio(player, "movietheatrecore." + i);
                    }
                }
            }
        }

        if (showThumbnail) {
            scheduler.runSync(() -> {
                screen.loadThumbnail();
                int[] ids = screen.getIds();
                List<ItemFrame> frames = screen.getFrames();
                for (int i = 0; i < frames.size() && i < ids.length; i++) {
                    ItemFrame frame = frames.get(i);
                    if (frame != null) {
                        frame.setItem(itemStacks.getMap(ids[i]));
                    }
                }
            });
        }

        viewers.clear();
        audioListeners.clear();
        state = PlaybackState.IDLE;
        stopping.set(false);
    }

    private void tick() {
        if (paused) {
            return;
        }

        updateViewers();
        int audioInterval = Math.max(1, configuration.theatre_audio_update_interval());
        if (audioUpdateCounter++ % audioInterval == 0) {
            updateAudioListeners();
        }

        long now = System.nanoTime();
        if (now - lastFrameNanos < frameDurationNanos) {
            return;
        }

        if (!rendering.compareAndSet(false, true)) {
            return;
        }

        int currentFrame = frameIndex;
        frameIndex++;
        lastFrameNanos = now;

        if (currentFrame >= video.getTotalFrames()) {
            if (video.isLoopping()) {
                frameIndex = 0;
                rendering.set(false);
                return;
            }
            onEnd();
            return;
        }

        List<UUID> viewerSnapshot = new ArrayList<>(viewers);

        AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
        BukkitTask task = scheduler.runAsync(() -> {
            try {
                renderFrame(currentFrame, viewerSnapshot);
            } finally {
                BukkitTask current = taskRef.get();
                if (current != null) {
                    renderTasks.remove(current);
                }
            }
        });
        if (task != null) {
            renderTasks.add(task);
            taskRef.set(task);
        }
    }

    private void onEnd() {
        rendering.set(false);
        stop(true);
        manager.clearSession(screen.getUUID(), ScreenState.IDLE);
    }

    private void renderFrame(int index, List<UUID> viewerSnapshot) {
        if (!active) {
            rendering.set(false);
            return;
        }
        File frameFile = new File(video.getFramesFolder(), index + video.getFramesExtension());
        if (!frameFile.exists()) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().warning("[MovieTheatreCore]: Missing frame " + frameFile.getName() + " for video " + video.getName()));
            rendering.set(false);
            return;
        }

        try {
            BufferedImage frame = ImageIO.read(frameFile);
            int targetWidth = screen.getWidth() * 128;
            int targetHeight = screen.getHeight() * 128;
            ScalingMode mode = screen.getScaleMode();
            BufferedImage scaled = scaler.scale(frame, targetWidth, targetHeight, mode);
            BufferedImage[] tiles = MapTileSplitter.split(scaled, screen.getWidth(), screen.getHeight());
            byte[][] buffers = new byte[tiles.length][];

            for (int i = 0; i < tiles.length; i++) {
                buffers[i] = MapColorPalette.convertImage(tiles[i]);
            }

            scheduler.runSync(() -> {
                if (active) {
                    updateMaps(buffers, viewerSnapshot);
                }
            });
        } catch (IOException e) {
            scheduler.runSync(() -> plugin.getLogger().warning("[MovieTheatreCore]: Failed to render frame " + index + " for video " + video.getName()));
            onError();
        } finally {
            rendering.set(false);
        }
    }

    private void updateMaps(byte[][] buffers, List<UUID> viewerSnapshot) {
        int[] ids = screen.getIds();
        for (int i = 0; i < ids.length && i < buffers.length; i++) {
            for (UUID uuid : viewerSnapshot) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    plugin.getMapUtil().update(player, ids[i], buffers[i]);
                }
            }
        }
    }

    private void updateViewers() {
        List<ItemFrame> frames = screen.getFrames();
        if (frames.isEmpty()) {
            return;
        }

        Location center = frames.get(frames.size() / 2).getLocation();
        Set<UUID> seen = new HashSet<>();

        for (Entity entity : getNearbyEntities(center, configuration.maximum_distance_to_receive())) {
            if (entity.getType() == EntityType.PLAYER) {
                Player player = (Player) entity;
                if (player.isOnline()) {
                    seen.add(player.getUniqueId());
                    viewers.add(player.getUniqueId());
                }
            }
        }

        viewers.retainAll(seen);
    }

    private void updateAudioListeners() {
        Location speaker = getAudioSpeakerLocation();
        if (speaker == null || speaker.getWorld() == null) {
            audioListeners.clear();
            return;
        }

        int radius = screen.getAudioRadius();
        Set<UUID> seen = new HashSet<>();

        for (Entity entity : getNearbyEntities(speaker, radius)) {
            if (entity.getType() == EntityType.PLAYER) {
                Player player = (Player) entity;
                if (player.isOnline() && player.getWorld().equals(speaker.getWorld()) && isAllowedAudioListener(player)) {
                    seen.add(player.getUniqueId());
                    if (!audioListeners.contains(player.getUniqueId())) {
                        handleAudioListenerJoin(player);
                    }
                }
            }
        }

        audioListeners.retainAll(seen);
    }

    private boolean isAllowedAudioListener(Player player) {
        if (audioAudienceFilter == null) {
            return true;
        }
        return audioAudienceFilter.test(player);
    }

    private void handleAudioListenerJoin(Player player) {
        audioListeners.add(player.getUniqueId());
        if (options.allowAudio()) {
            if (audioTrack != null) {
                sendResourcePack(player, audioTrack.getPackUrl(), audioTrack.getPackSha1());
            } else if (video.isAudioEnabled() && resourcePackServer != null) {
                sendResourcePack(player, resourcePackServer.url().replaceAll("%name%", video.getName() + ".zip"), new byte[0]);
                for (int i = 0; i < video.getAudioChannels(); i++) {
                    player.playSound(player.getLocation(), "movietheatrecore." + i, 10, 1);
                }
            }
        }
    }

    private void onError() {
        state = PlaybackState.ERROR;
        stop(true);
        manager.clearSession(screen.getUUID(), ScreenState.ERROR);
    }

    private void setupResourcePack() {
        if (!options.allowAudio()) {
            return;
        }
        if (options.audioTrack() != null) {
            audioTrack = options.audioTrack();
            return;
        }
        if (!video.isAudioEnabled()) {
            return;
        }

        VideoData data = video.getVideoData();
        File pack = new File(data.getResourcePacksFolder(), video.getName() + ".zip");
        if (!pack.exists()) {
            return;
        }
        resourcePackServer = new Server(pack);
        resourcePackServer.start();
    }

    private void sendResourcePack(Player player, String url, byte[] sha1) {
        if (player == null || url == null || url.isEmpty()) {
            return;
        }
        try {
            if (sha1 != null && sha1.length > 0) {
                player.setResourcePack(url, sha1, true);
            } else {
                player.setResourcePack(url);
            }
        } catch (NoSuchMethodError error) {
            player.setResourcePack(url);
        }
    }

    private Set<UUID> getAudioListenerSnapshot() {
        return new HashSet<>(audioListeners);
    }

    private Location getAudioSpeakerLocation() {
        return screen.getAudioSpeakerLocation();
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
