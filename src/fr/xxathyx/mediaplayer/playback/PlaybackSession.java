package fr.xxathyx.mediaplayer.playback;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.configuration.Configuration;
import fr.xxathyx.mediaplayer.items.ItemStacks;
import fr.xxathyx.mediaplayer.map.colors.MapColorPalette;
import fr.xxathyx.mediaplayer.render.FrameScaler;
import fr.xxathyx.mediaplayer.render.MapTileSplitter;
import fr.xxathyx.mediaplayer.render.ScalingMode;
import fr.xxathyx.mediaplayer.screen.Screen;
import fr.xxathyx.mediaplayer.screen.ScreenState;
import fr.xxathyx.mediaplayer.server.Server;
import fr.xxathyx.mediaplayer.video.Video;
import fr.xxathyx.mediaplayer.video.data.VideoData;

public class PlaybackSession {

    private final Main plugin;
    private final Configuration configuration;
    private final Screen screen;
    private final Video video;
    private final PlaybackManager manager;
    private final UUID sessionId;
    private final FrameScaler scaler = new FrameScaler();
    private final ItemStacks itemStacks = new ItemStacks();
    private final AtomicBoolean rendering = new AtomicBoolean(false);

    private final Set<UUID> viewers = new HashSet<>();

    private BukkitTask tickTask;
    private boolean paused = false;
    private volatile boolean active = true;
    private int frameIndex = 0;
    private long lastFrameNanos = 0L;
    private long frameDurationNanos;

    private Server resourcePackServer;

    public PlaybackSession(Main plugin, Screen screen, Video video, PlaybackManager manager) {
        this.plugin = plugin;
        this.configuration = new Configuration();
        this.screen = screen;
        this.video = video;
        this.manager = manager;
        this.sessionId = UUID.randomUUID();
        this.frameDurationNanos = (long) (1_000_000_000L / Math.max(1.0, video.getFrameRate()));
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

    public void start() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        active = true;
        setupResourcePack();
        lastFrameNanos = System.nanoTime();

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public void stop(boolean showThumbnail) {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = null;
        paused = false;
        active = false;
        rendering.set(false);

        if (resourcePackServer != null) {
            resourcePackServer.stop();
            resourcePackServer = null;
        }

        if (video.isAudioEnabled()) {
            for (UUID uuid : viewers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    for (int i = 0; i < video.getAudioChannels(); i++) {
                        plugin.getAudioUtil().stopAudio(player, "mediaplayer." + i);
                    }
                }
            }
        }

        if (showThumbnail) {
            Bukkit.getScheduler().runTask(plugin, () -> {
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
    }

    private void tick() {
        if (paused) {
            return;
        }

        updateViewers();

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
            renderEnd();
            return;
        }

        List<UUID> viewerSnapshot = new ArrayList<>(viewers);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> renderFrame(currentFrame, viewerSnapshot));
    }

    private void renderEnd() {
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
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().warning("[MediaPlayer]: Missing frame " + frameFile.getName() + " for video " + video.getName()));
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

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (active) {
                    updateMaps(buffers, viewerSnapshot);
                }
            });
        } catch (IOException e) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().warning("[MediaPlayer]: Failed to render frame " + index + " for video " + video.getName()));
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
                    if (!viewers.contains(player.getUniqueId())) {
                        handleViewerJoin(player);
                    }
                }
            }
        }

        viewers.retainAll(seen);
    }

    private void handleViewerJoin(Player player) {
        viewers.add(player.getUniqueId());
        if (video.isAudioEnabled() && resourcePackServer != null) {
            player.setResourcePack(resourcePackServer.url().replaceAll("%name%", video.getName() + ".zip"));
            for (int i = 0; i < video.getAudioChannels(); i++) {
                player.playSound(player.getLocation(), "mediaplayer." + i, 10, 1);
            }
        }
    }

    private void setupResourcePack() {
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
