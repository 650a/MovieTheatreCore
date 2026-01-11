package fr.xxathyx.mediaplayer.audio;

public class AudioTrack {

    private final String mediaId;
    private final int chunkCount;
    private final int chunkSeconds;
    private final String packUrl;
    private final byte[] packSha1;

    public AudioTrack(String mediaId, int chunkCount, int chunkSeconds, String packUrl, byte[] packSha1) {
        this.mediaId = mediaId;
        this.chunkCount = chunkCount;
        this.chunkSeconds = chunkSeconds;
        this.packUrl = packUrl;
        this.packSha1 = packSha1;
    }

    public String getMediaId() {
        return mediaId;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public int getChunkSeconds() {
        return chunkSeconds;
    }

    public String getPackUrl() {
        return packUrl;
    }

    public byte[] getPackSha1() {
        return packSha1;
    }
}
