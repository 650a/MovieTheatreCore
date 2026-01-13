package com._650a.movietheatrecore.theatre;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.screen.ScreenManager;

public class TheatreRoom {

    private final UUID id;
    private String name;
    private final List<String> screens;
    private AudioZone audioZone;
    private final List<TheatreSeat> seats;

    public TheatreRoom(UUID id, String name, List<String> screens, AudioZone audioZone, List<TheatreSeat> seats) {
        this.id = id;
        this.name = name;
        this.screens = screens == null ? new ArrayList<>() : new ArrayList<>(screens);
        this.audioZone = audioZone;
        this.seats = seats == null ? new ArrayList<>() : new ArrayList<>(seats);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getScreens() {
        return Collections.unmodifiableList(screens);
    }

    public void addScreen(String screenName) {
        if (screenName == null || screenName.isBlank()) {
            return;
        }
        if (!screens.contains(screenName)) {
            screens.add(screenName);
        }
    }

    public void removeScreen(String screenName) {
        screens.remove(screenName);
    }

    public AudioZone getAudioZone() {
        return audioZone;
    }

    public void setAudioZone(AudioZone audioZone) {
        this.audioZone = audioZone;
    }

    public List<TheatreSeat> getSeats() {
        return Collections.unmodifiableList(seats);
    }

    public List<Screen> resolveScreens(ScreenManager screenManager) {
        if (screenManager == null) {
            return Collections.emptyList();
        }
        List<Screen> resolved = new ArrayList<>();
        for (String name : screens) {
            Screen screen = screenManager.getScreenByName(name);
            if (screen != null) {
                resolved.add(screen);
            }
        }
        return resolved;
    }
}
