package fr.xxathyx.mediaplayer.playback;

import fr.xxathyx.mediaplayer.audio.AudioTrack;
import fr.xxathyx.mediaplayer.media.MediaEntry;

public record PlaybackOptions(boolean allowAudio, MediaEntry mediaEntry, AudioTrack audioTrack) {

    public static PlaybackOptions defaultOptions() {
        return new PlaybackOptions(true, null, null);
    }
}
