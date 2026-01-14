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
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
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
    private final Set<UUID> packPending = new HashSet<>();
    private final Set<UUID> packApplied = new HashSet<>();
    private final Set<UUID> audioUnavailableNotified = new HashSet<>();

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
    private boolean packRequired = false;

    private Server resourcePackServer;
    private AudioTrack audioTrack;
    private String lastSkipReason;
    private long lastSkipLogAt = 0L;
    private long lastFrameLogAt = 0L;
    private int lastLoggedFrame = -1;
    private long lastScreenDebugAt = 0L;
    private long lastMapSendAt = 0L;
    private long mapSendWindowStart = 0L;
    private int mapSendWindowCount = 0;
    private int lastMapSendRate = 0;

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
        if (filter == null) {
            return;
        }
        if (this.audioAudienceFilter == null) {
            this.audioAudienceFilter = filter;
            return;
        }
        java.util.function.Predicate<Player> existing = this.audioAudienceFilter;
        this.audioAudienceFilter = player -> existing.test(player) && filter.test(player);
    }

    public void start() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        state = PlaybackState.PLAYING;
        active = true;
        setupResourcePack();
        ensureScreenMaps();
        logScreenDebugSnapshot("start");
        lastFrameNanos = System.nanoTime();

        tickTask = scheduler.runSyncRepeating(this::tick, 0L, 1L);
        startAudioPlaybackIfReady();
        if (configuration.debug_render()) {
            plugin.getLogger().info("[MovieTheatreCore]: Renderer started for screen " + screen.getName() + " (video=" + video.getName() + ").");
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
                    clearResourcePack(player);
                }
            }
        }
        if (configuration.debug_render()) {
            plugin.getLogger().info("[MovieTheatreCore]: Renderer stopped for screen " + screen.getName() + " (video=" + video.getName() + ").");
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
        packPending.clear();
        packApplied.clear();
        audioUnavailableNotified.clear();
        state = PlaybackState.IDLE;
        stopping.set(false);
    }

    private void tick() {
        if (paused) {
            logRenderSkip("paused");
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
        if (viewerSnapshot.isEmpty()) {
            logRenderSkip("no viewers within render radius");
            rendering.set(false);
            return;
        }

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
            logRenderSkip("session inactive");
            rendering.set(false);
            return;
        }
        File frameFile = new File(video.getFramesFolder(), index + video.getFramesExtension());
        if (!frameFile.exists()) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().warning("[MovieTheatreCore]: Missing frame " + frameFile.getName() + " for video " + video.getName()));
            logRenderSkip("missing frame " + frameFile.getName());
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
        recordMapSend();
        logScreenDebugSnapshot("frame");
        if (configuration.debug_render()) {
            long now = System.currentTimeMillis();
            if (now - lastFrameLogAt > 1000L || lastLoggedFrame != frameIndex) {
                lastFrameLogAt = now;
                lastLoggedFrame = frameIndex;
                plugin.getLogger().info("[MovieTheatreCore]: Sent frame " + (frameIndex - 1) + " to " + viewerSnapshot.size() + " viewers (" + buffers.length + " map tiles).");
            }
        }
    }

    private void ensureScreenMaps() {
        List<ItemFrame> frames = screen.getFrames();
        int[] ids = screen.getIds();
        if (frames.isEmpty() || ids.length == 0) {
            if (configuration.debug_render()) {
                plugin.getLogger().warning("[MovieTheatreCore]: Screen " + screen.getName() + " is missing frames or map ids; rendering may be skipped.");
            }
            return;
        }
        for (int i = 0; i < frames.size() && i < ids.length; i++) {
            ItemFrame frame = frames.get(i);
            if (frame == null) {
                continue;
            }
            ItemStack item = frame.getItem();
            if (plugin.isLegacy()) {
                frame.setItem(itemStacks.getMap(ids[i]));
                continue;
            }
            boolean needsUpdate = true;
            if (item != null && item.getType() == Material.FILLED_MAP && item.getItemMeta() instanceof MapMeta meta) {
                int mapId = plugin.getMapUtil().getMapId(meta.getMapView());
                needsUpdate = mapId != ids[i];
            }
            if (needsUpdate) {
                frame.setItem(itemStacks.getMap(ids[i]));
            }
        }
        logScreenDebugSnapshot("maps");
    }

    private void logScreenDebugSnapshot(String context) {
        if (!configuration.debug_screens()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScreenDebugAt < 5000L) {
            return;
        }
        lastScreenDebugAt = now;
        int[] ids = screen.getIds();
        List<ItemFrame> frames = screen.getFrames();
        int filledMaps = 0;
        boolean renderersAttached = true;
        for (int i = 0; i < ids.length; i++) {
            org.bukkit.map.MapView mapView = plugin.getMapUtil().getMapView(ids[i]);
            if (mapView == null || mapView.getRenderers().isEmpty()) {
                renderersAttached = false;
            }
        }
        for (int i = 0; i < frames.size() && i < ids.length; i++) {
            ItemFrame frame = frames.get(i);
            if (frame != null && frame.getItem() != null && frame.getItem().getType() == Material.FILLED_MAP) {
                filledMaps++;
            }
        }
        plugin.getLogger().info("[MovieTheatreCore]: Screen debug (" + context + ") name=" + screen.getName()
                + " uuid=" + screen.getUUID()
                + " mapIds=" + java.util.Arrays.toString(ids)
                + " renderersAttached=" + renderersAttached
                + " filledMaps=" + filledMaps + "/" + ids.length
                + " viewers=" + getViewerCount()
                + " frameIndex=" + frameIndex);
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
            for (UUID uuid : new HashSet<>(audioListeners)) {
                handleAudioListenerLeave(uuid);
            }
            audioListeners.clear();
            packPending.clear();
            packApplied.clear();
            return;
        }

        int radius = screen.getAudioRadius();
        Set<UUID> previous = new HashSet<>(audioListeners);
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
        for (UUID uuid : previous) {
            if (!audioListeners.contains(uuid)) {
                handleAudioListenerLeave(uuid);
            }
        }
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
                String packUrl = audioTrack.getPackUrl();
                byte[] sha1 = audioTrack.getPackSha1();
                if (manager.shouldSendResourcePack(player, packUrl, sha1)) {
                    sendResourcePack(player, packUrl, sha1);
                    manager.markResourcePackSent(player, packUrl, sha1);
                    markPackPending(player);
                }
            } else if (video.isAudioEnabled() && resourcePackServer != null) {
                String packUrl = configuration.resolveResourcePackUrl();
                if (packUrl != null && !packUrl.isBlank()) {
                    if (manager.shouldSendResourcePack(player, packUrl, new byte[0])) {
                        sendResourcePack(player, packUrl, new byte[0]);
                        manager.markResourcePackSent(player, packUrl, new byte[0]);
                        markPackPending(player);
                        for (int i = 0; i < video.getAudioChannels(); i++) {
                            player.playSound(player.getLocation(), "movietheatrecore." + i, 10, 1);
                        }
                    }
                } else if (configuration.debug_pack()) {
                    plugin.getLogger().warning("[MovieTheatreCore]: Skipping resource pack send for " + player.getName() + " because no public pack URL is configured.");
                }
            }
        } else if (video.isAudioEnabled()) {
            notifyAudioUnavailable(player);
        }
    }

    public void handleResourcePackStatus(Player player, org.bukkit.event.player.PlayerResourcePackStatusEvent.Status status) {
        if (!packRequired || player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!audioListeners.contains(uuid)) {
            return;
        }
        if (status == org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            packApplied.add(uuid);
            packPending.remove(uuid);
            if (configuration.debug_pack()) {
                plugin.getLogger().info("[MovieTheatreCore]: Resource pack loaded for " + player.getName() + ".");
            }
            startAudioPlaybackIfReady();
            return;
        }
        if (status == org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.DECLINED
                || status == org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            notifyPackFailure(player, status.name());
            packPending.remove(uuid);
            packApplied.remove(uuid);
            audioListeners.remove(uuid);
            if (configuration.debug_pack()) {
                plugin.getLogger().warning("[MovieTheatreCore]: Resource pack status " + status.name() + " for " + player.getName() + ".");
            }
            clearResourcePack(player);
            startAudioPlaybackIfReady();
            notifyAudioUnavailable(player);
        }
    }

    private void handleAudioListenerLeave(UUID uuid) {
        packPending.remove(uuid);
        packApplied.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && options.allowAudio()) {
            if (manager != null && manager.shouldKeepResourcePack(player)) {
                return;
            }
            clearResourcePack(player);
        }
    }

    private void markPackPending(Player player) {
        if (!packRequired || player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!packApplied.contains(uuid)) {
            packPending.add(uuid);
        }
    }

    private void startAudioPlaybackIfReady() {
        if (audioTrack == null || audioPlayback != null) {
            return;
        }
        if (packRequired && !audioListeners.isEmpty()) {
            if (!packPending.isEmpty()) {
                return;
            }
            for (UUID uuid : audioListeners) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && manager.getPackStatus(player) != ResourcePackTracker.PackStatus.ACCEPTED) {
                    return;
                }
            }
        }
        lastFrameNanos = System.nanoTime();
        audioPlayback = new AudioPlayback(scheduler, audioTrack, this::getAudioListenerSnapshot, this::getAudioSpeakerLocation, () -> active);
        audioPlayback.start();
    }

    private void notifyPackFailure(Player player, String status) {
        String packUrl = audioTrack == null ? "unknown" : audioTrack.getPackUrl();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("movietheatrecore.admin")) {
                online.sendMessage(ChatColor.RED + "MovieTheatreCore pack failed (" + status + ") for " + player.getName() + ". Video will continue without audio.");
                online.sendMessage(ChatColor.YELLOW + "Check pack URL: " + packUrl);
                online.sendMessage(ChatColor.YELLOW + "Run /mtc debug pack for diagnostics.");
            }
        }
    }

    private void notifyAudioUnavailable(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (audioUnavailableNotified.contains(uuid)) {
            return;
        }
        audioUnavailableNotified.add(uuid);
        String message = ChatColor.YELLOW + "Audio requires the resource pack; video will still play.";
        try {
            plugin.getActionBar().send(player, message);
        } catch (Exception ignored) {
            player.sendMessage(message);
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
            packRequired = true;
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
        packRequired = true;
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
        if (configuration.debug_pack()) {
            plugin.getLogger().info("[MovieTheatreCore]: Sent resource pack to " + player.getName() + " url=" + url + " sha1=" + (sha1 == null ? "none" : sha1.length + " bytes") + ".");
        }
    }

    private void clearResourcePack(Player player) {
        if (player == null) {
            return;
        }
        try {
            java.lang.reflect.Method method = player.getClass().getMethod("removeResourcePack");
            method.invoke(player);
            if (configuration.debug_pack()) {
                plugin.getLogger().info("[MovieTheatreCore]: Removed resource pack for " + player.getName() + ".");
            }
            return;
        } catch (NoSuchMethodException ignored) {
            // Fall through to legacy safe behavior.
        } catch (Exception e) {
            if (configuration.debug_pack()) {
                plugin.getLogger().warning("[MovieTheatreCore]: Failed to remove resource pack for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private void logRenderSkip(String reason) {
        if (!configuration.debug_render()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (reason.equals(lastSkipReason) && now - lastSkipLogAt < 5000L) {
            return;
        }
        lastSkipReason = reason;
        lastSkipLogAt = now;
        plugin.getLogger().info("[MovieTheatreCore]: Rendering skipped for screen " + screen.getName() + ": " + reason + ".");
    }

    public int getCurrentFrameIndex() {
        return frameIndex;
    }

    public int getViewerCount() {
        return viewers.size();
    }

    public int getAudioListenerCount() {
        return audioListeners.size();
    }

    public long getLastMapSendAt() {
        return lastMapSendAt;
    }

    public int getLastMapSendRate() {
        return lastMapSendRate;
    }

    public PlaybackState getState() {
        return state;
    }

    public boolean isAudioEligibleForListeners() {
        return options.allowAudio() && video.isAudioEnabled();
    }

    private Set<UUID> getAudioListenerSnapshot() {
        return new HashSet<>(audioListeners);
    }

    private Location getAudioSpeakerLocation() {
        return screen.getAudioSpeakerLocation();
    }

    private void recordMapSend() {
        long now = System.currentTimeMillis();
        lastMapSendAt = now;
        if (mapSendWindowStart == 0L) {
            mapSendWindowStart = now;
        }
        if (now - mapSendWindowStart >= 1000L) {
            lastMapSendRate = mapSendWindowCount;
            mapSendWindowStart = now;
            mapSendWindowCount = 0;
        }
        mapSendWindowCount++;
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
