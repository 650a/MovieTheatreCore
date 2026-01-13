package com._650a.movietheatrecore.playback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    public PlaybackManager(Main plugin, ScreenManager screenManager) {
        this.plugin = plugin;
        this.screenManager = screenManager;
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
        for (PlaybackSession session : sessions.values()) {
            session.handleResourcePackStatus(player, status);
        }
    }
}
