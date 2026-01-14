package com._650a.movietheatrecore.playback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.screen.ScreenManager;
import com._650a.movietheatrecore.screen.ScreenState;
import com._650a.movietheatrecore.video.Video;

public class PlaybackManager {

    private final Main plugin;
    private final ScreenManager screenManager;
    private final Map<UUID, PlaybackSession> sessions = new HashMap<>();
    private final ResourcePackTracker packTracker;

    public PlaybackManager(Main plugin, ScreenManager screenManager) {
        this.plugin = plugin;
        this.screenManager = screenManager;
        this.packTracker = new ResourcePackTracker(new com._650a.movietheatrecore.configuration.Configuration());
    }

    public PlaybackSession start(Screen screen, Video video) {
        return start(screen, video, PlaybackOptions.defaultOptions());
    }

    public PlaybackSession start(Screen screen, Video video, PlaybackOptions options) {
        stop(screen, ScreenState.STOPPING, false);
        screenManager.setState(screen.getUUID(), ScreenState.PREPARING);

        try {
            screen.setVideoName(video.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("[MovieTheatreCore]: Failed to store last video name for screen " + screen.getName());
        }

        PlaybackSession session = new PlaybackSession(plugin, screen, video, this, options);
        session.setAudioAudienceFilter(player -> isClosestAudioSession(screen, player));
        sessions.put(screen.getUUID(), session);
        screenManager.setState(screen.getUUID(), ScreenState.PLAYING);
        session.start();
        return session;
    }

    public void pause(Screen screen) {
        PlaybackSession session = sessions.get(screen.getUUID());
        if (session != null) {
            session.pause();
        }
    }

    public void resume(Screen screen) {
        PlaybackSession session = sessions.get(screen.getUUID());
        if (session != null) {
            session.resume();
        }
    }

    public void stop(Screen screen, ScreenState state, boolean showThumbnail) {
        PlaybackSession session = sessions.remove(screen.getUUID());
        if (session != null) {
            session.stop(showThumbnail);
        }
        screenManager.setState(screen.getUUID(), state == null ? ScreenState.IDLE : state);
    }

    public void stopAll() {
        for (PlaybackSession session : sessions.values()) {
            session.stop(true);
        }
        sessions.clear();
        for (UUID screenId : screenManager.getScreens().keySet()) {
            screenManager.setState(screenId, ScreenState.IDLE);
        }
    }

    public PlaybackSession getSession(Screen screen) {
        return sessions.get(screen.getUUID());
    }

    public void clearSession(UUID screenId, ScreenState state) {
        sessions.remove(screenId);
        screenManager.setState(screenId, state == null ? ScreenState.IDLE : state);
    }

    public void handleResourcePackStatus(Player player, Status status) {
        packTracker.recordStatus(player, status);
        for (PlaybackSession session : sessions.values()) {
            session.handleResourcePackStatus(player, status);
        }
    }

    public boolean shouldSendResourcePack(Player player, String url, byte[] sha1) {
        return packTracker.shouldSend(player, url, sha1);
    }

    public void markResourcePackSent(Player player, String url, byte[] sha1) {
        packTracker.markSent(player, url, sha1);
    }

    public ResourcePackTracker.PackStatus getPackStatus(Player player) {
        return packTracker.getStatus(player);
    }

    public void resetPackState(Player player) {
        packTracker.clear(player);
    }

    public boolean shouldKeepResourcePack(Player player) {
        if (player == null) {
            return false;
        }
        for (PlaybackSession session : sessions.values()) {
            if (session == null || !session.isAudioEligibleForListeners()) {
                continue;
            }
            Screen screen = session.getScreen();
            if (screen == null) {
                continue;
            }
            if (isClosestAudioSession(screen, player)) {
                return true;
            }
        }
        return false;
    }

    private boolean isClosestAudioSession(Screen screen, Player player) {
        if (player == null || screen == null) {
            return false;
        }
        Location playerLocation = player.getLocation();
        if (playerLocation == null) {
            return false;
        }
        double closestDistance = Double.MAX_VALUE;
        Screen closestScreen = null;
        for (PlaybackSession session : sessions.values()) {
            if (session == null || !session.isAudioEligibleForListeners()) {
                continue;
            }
            Screen candidate = session.getScreen();
            if (candidate == null) {
                continue;
            }
            Location speaker = candidate.getAudioSpeakerLocation();
            if (speaker == null || speaker.getWorld() == null || !speaker.getWorld().equals(playerLocation.getWorld())) {
                continue;
            }
            int radius = candidate.getAudioRadius();
            double distance = speaker.distance(playerLocation);
            if (distance > radius) {
                continue;
            }
            if (distance < closestDistance) {
                closestDistance = distance;
                closestScreen = candidate;
            } else if (distance == closestDistance && closestScreen != null) {
                String currentId = candidate.getUUID() == null ? "" : candidate.getUUID().toString();
                String closestId = closestScreen.getUUID() == null ? "" : closestScreen.getUUID().toString();
                if (currentId.compareToIgnoreCase(closestId) < 0) {
                    closestScreen = candidate;
                }
            }
        }
        if (closestScreen == null) {
            return false;
        }
        UUID closestId = closestScreen.getUUID();
        UUID targetId = screen.getUUID();
        return closestId != null && closestId.equals(targetId);
    }
}
