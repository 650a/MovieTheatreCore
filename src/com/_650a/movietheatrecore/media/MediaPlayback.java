package com._650a.movietheatrecore.media;

import com._650a.movietheatrecore.playback.PlaybackOptions;
import com._650a.movietheatrecore.video.Video;

public class MediaPlayback {

    private final Video video;
    private final PlaybackOptions options;
    private final MediaEntry entry;

    public MediaPlayback(Video video, PlaybackOptions options, MediaEntry entry) {
        this.video = video;
        this.options = options;
        this.entry = entry;
    }

    public Video getVideo() {
        return video;
    }

    public PlaybackOptions getOptions() {
        return options;
    }

    public MediaEntry getEntry() {
        return entry;
    }
}
