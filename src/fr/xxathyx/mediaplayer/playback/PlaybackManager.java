package fr.xxathyx.mediaplayer.playback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.screen.Screen;
import fr.xxathyx.mediaplayer.screen.ScreenManager;
import fr.xxathyx.mediaplayer.screen.ScreenState;
import fr.xxathyx.mediaplayer.video.Video;

public class PlaybackManager {

    private final Main plugin;
    private final ScreenManager screenManager;
    private final Map<UUID, PlaybackSession> sessions = new HashMap<>();

    public PlaybackManager(Main plugin, ScreenManager screenManager) {
        this.plugin = plugin;
        this.screenManager = screenManager;
    }

    public PlaybackSession start(Screen screen, Video video) {
        stop(screen, ScreenState.STOPPING);
        screenManager.setState(screen.getUUID(), ScreenState.PREPARING);

        try {
            screen.setVideoName(video.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("[MediaPlayer]: Failed to store last video name for screen " + screen.getName());
        }

        PlaybackSession session = new PlaybackSession(plugin, screen, video, this);
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

    public void stop(Screen screen, ScreenState state) {
        PlaybackSession session = sessions.remove(screen.getUUID());
        if (session != null) {
            session.stop(true);
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
}
