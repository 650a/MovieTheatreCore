package com._650a.movietheatrecore.theatre;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ShowScheduleEntry {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UUID id;
    private String mediaId;
    private LocalDateTime nextRun;
    private ShowRepeat repeat;
    private boolean enabled;
    private LocalDateTime lastTriggered;

    public ShowScheduleEntry(UUID id, String mediaId, LocalDateTime nextRun, ShowRepeat repeat, boolean enabled) {
        this.id = id;
        this.mediaId = mediaId;
        this.nextRun = nextRun;
        this.repeat = repeat == null ? ShowRepeat.NONE : repeat;
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public LocalDateTime getNextRun() {
        return nextRun;
    }

    public void setNextRun(LocalDateTime nextRun) {
        this.nextRun = nextRun;
    }

    public ShowRepeat getRepeat() {
        return repeat;
    }

    public void setRepeat(ShowRepeat repeat) {
        this.repeat = repeat == null ? ShowRepeat.NONE : repeat;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getLastTriggered() {
        return lastTriggered;
    }

    public void setLastTriggered(LocalDateTime lastTriggered) {
        this.lastTriggered = lastTriggered;
    }

    public boolean isDue(LocalDateTime now) {
        if (!enabled || nextRun == null) {
            return false;
        }
        return !now.isBefore(nextRun);
    }

    public void markTriggered(LocalDateTime now) {
        lastTriggered = now;
        if (repeat == ShowRepeat.NONE) {
            enabled = false;
            return;
        }
        LocalDateTime candidate = nextRun;
        if (repeat == ShowRepeat.DAILY) {
            candidate = candidate.plusDays(1);
            while (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1);
            }
        } else if (repeat == ShowRepeat.WEEKLY) {
            candidate = candidate.plusWeeks(1);
            while (!candidate.isAfter(now)) {
                candidate = candidate.plusWeeks(1);
            }
        }
        nextRun = candidate;
    }
}
