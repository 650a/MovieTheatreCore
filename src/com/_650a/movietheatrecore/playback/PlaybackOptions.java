package com._650a.movietheatrecore.playback;

import com._650a.movietheatrecore.audio.AudioTrack;
import com._650a.movietheatrecore.media.MediaEntry;

public record PlaybackOptions(boolean allowAudio, MediaEntry mediaEntry, AudioTrack audioTrack) {

    public static PlaybackOptions defaultOptions() {
        return new PlaybackOptions(true, null, null);
    }
}
