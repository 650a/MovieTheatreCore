package com._650a.movietheatrecore.media;

public class MediaEntry {

    private final String name;
    private final String url;
    private final String id;
    private final String extension;
    private final boolean libraryEntry;
    private long sizeBytes;
    private long lastAccess;
    private String audioSha1;
    private int audioChunks;

    public MediaEntry(String name, String url, String id, String extension, boolean libraryEntry) {
        this.name = name;
        this.url = url;
        this.id = id;
        this.extension = extension;
        this.libraryEntry = libraryEntry;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getId() {
        return id;
    }

    public String getExtension() {
        return extension;
    }

    public boolean isLibraryEntry() {
        return libraryEntry;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public long getLastAccess() {
        return lastAccess;
    }

    public void setLastAccess(long lastAccess) {
        this.lastAccess = lastAccess;
    }

    public String getAudioSha1() {
        return audioSha1;
    }

    public void setAudioSha1(String audioSha1) {
        this.audioSha1 = audioSha1;
    }

    public int getAudioChunks() {
        return audioChunks;
    }

    public void setAudioChunks(int audioChunks) {
        this.audioChunks = audioChunks;
    }
}
