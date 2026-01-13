package com._650a.movietheatrecore.audio;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.bukkit.command.CommandSender;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.media.MediaEntry;
import com._650a.movietheatrecore.resourcepack.EmbeddedPackServer;
import com._650a.movietheatrecore.resourcepack.ResourcePack;
import com._650a.movietheatrecore.util.Scheduler;

public class AudioPackManager {

    private final Main plugin;
    private final Configuration configuration;
    private final Scheduler scheduler;
    private final EmbeddedPackServer packServer;
    private final Object buildLock = new Object();
    private boolean warnedMissingPackUrl = false;

    private final File packFolder;
    private final File packFile;

    public AudioPackManager(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
        this.scheduler = new Scheduler(plugin);
        this.packFolder = new File(configuration.getResourcePackFolder(), "pack");
        this.packFile = new File(configuration.getResourcePackFolder(), "pack.zip");
        this.packServer = new EmbeddedPackServer(plugin, configuration, configuration.getResourcePackFolder());
    }

    public void startServer() {
        packServer.start();
    }

    public AudioTrack prepare(MediaEntry entry, File mediaFile) throws IOException {
        if (!configuration.audio_enabled()) {
            return null;
        }
        String packUrl = resolvePackUrl();
        if (packUrl == null || packUrl.isBlank()) {
            warnMissingPackUrl();
            return null;
        }

        boolean entryChanged = ensureAudioChunks(entry, mediaFile);
        ensurePackReady(entryChanged);

        int chunkCount = entry.getAudioChunks();
        if (chunkCount <= 0) {
            chunkCount = countChunks(entry);
            entry.setAudioChunks(chunkCount);
        }

        byte[] sha1 = decodeSha1(configuration.resourcepack_sha1());
        return new AudioTrack(entry.getId(), chunkCount, configuration.audio_chunk_seconds(), packUrl, sha1);
    }

    public void rebuildPackAsync(CommandSender sender) {
        scheduler.runAsync(() -> {
            try {
                ensurePackReady(true);
                String sha1 = configuration.resourcepack_sha1();
                String url = resolvePackUrl();
                scheduler.runSync(() -> sender.sendMessage("Resource pack rebuilt. URL: " + (url == null ? "n/a" : url) + " SHA1: " + sha1));
            } catch (IOException e) {
                scheduler.runSync(() -> sender.sendMessage("Failed to rebuild resource pack: " + e.getMessage()));
            }
        });
    }

    public void stopAll() {
        packServer.stop();
    }

    public boolean isServerRunning() {
        return packServer.isRunning();
    }

    public String getServerError() {
        return packServer.getLastError();
    }

    public String getPackUrl() {
        return resolvePackUrl();
    }

    public String getPackSha1() {
        return configuration.resourcepack_sha1();
    }

    public long getLastBuildMillis() {
        return configuration.resourcepack_last_build();
    }

    public String getPackAssetsHash() {
        return configuration.resourcepack_assets_hash();
    }

    public EmbeddedPackServer getPackServer() {
        return packServer;
    }

    private void warnMissingPackUrl() {
        if (!warnedMissingPackUrl) {
            plugin.getLogger().warning("[MovieTheatreCore]: audio enabled but no pack URL configured (enable internal server or set resource_pack.url).");
            warnedMissingPackUrl = true;
        }
    }

    private void ensurePackReady(boolean force) throws IOException {
        synchronized (buildLock) {
            String assetsHash = computeAssetsHash();
            boolean missing = !packFile.exists();
            boolean dirty = force || missing || !assetsHash.equals(configuration.resourcepack_assets_hash());
            if (!dirty) {
                return;
            }
            buildPack(assetsHash);
        }
    }

    private boolean ensureAudioChunks(MediaEntry entry, File mediaFile) throws IOException {
        File chunkFolder = getAudioChunksFolder(entry);
        File[] chunks = chunkFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
        boolean missing = chunks == null || chunks.length == 0;
        if (missing) {
            if (chunkFolder.exists()) {
                FileUtils.deleteDirectory(chunkFolder);
            }
            chunkFolder.mkdirs();
            extractChunks(mediaFile, chunkFolder);
        }

        String signature = computeChunkSignature(chunkFolder);
        boolean changed = signature != null && !signature.equals(entry.getAudioSha1());
        if (signature != null) {
            entry.setAudioSha1(signature);
        }

        int count = countChunks(entry);
        entry.setAudioChunks(count);
        return missing || changed;
    }

    private File getAudioChunksFolder(MediaEntry entry) {
        return new File(configuration.getAudioChunksFolder(), entry.getId());
    }

    private void buildPack(String assetsHash) throws IOException {
        if (packFolder.exists()) {
            FileUtils.deleteDirectory(packFolder);
        }
        packFolder.mkdirs();

        createPackMetadata(packFolder);
        Map<String, Object> soundsMap = new HashMap<>();
        copyChunksToPack(packFolder, soundsMap);
        writeSoundsJson(packFolder, soundsMap);

        File tempZip = new File(configuration.getTempDir(), "pack.zip.tmp");
        dev.jeka.core.api.file.JkPathTree.of(packFolder.toPath()).zipTo(tempZip.toPath());
        Files.move(tempZip.toPath(), packFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        String sha1 = computeSha1(packFile);
        configuration.set_resourcepack_sha1(sha1);
        configuration.set_resourcepack_assets_hash(assetsHash);
        configuration.set_resourcepack_last_build(System.currentTimeMillis());

        String url = resolvePackUrl();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(configuration.resourcepack_last_build()));
        plugin.getLogger().info("[MovieTheatreCore]: Resource pack built at " + timestamp + " (SHA1: " + sha1 + ", url: " + (url == null ? "n/a" : url) + ").");
    }

    private void createPackMetadata(File packFolder) throws IOException {
        File metaFile = new File(packFolder, "pack.mcmeta");
        Map<String, Object> pack = new HashMap<>();
        pack.put("pack_format", new ResourcePack().getResourcePackFormat());
        pack.put("description", "MovieTheatreCore audio pack");

        Map<String, Object> root = new HashMap<>();
        root.put("pack", pack);

        try (Writer writer = new FileWriter(metaFile)) {
            new Gson().toJson(root, writer);
        }

        File iconFile = new File(packFolder, "pack.png");
        try {
            java.net.URL packUrl = Main.class.getResource("resources/audio.png");
            java.awt.image.BufferedImage buffered = packUrl == null
                    ? new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    : javax.imageio.ImageIO.read(packUrl);
            javax.imageio.ImageIO.write(buffered, "png", iconFile);
        } catch (IOException e) {
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", iconFile);
        }
    }

    private void copyChunksToPack(File packFolder, Map<String, Object> soundsMap) throws IOException {
        File chunkRoot = configuration.getAudioChunksFolder();
        File[] entries = chunkRoot.listFiles(File::isDirectory);
        if (entries == null) {
            return;
        }
        Arrays.sort(entries, Comparator.comparing(File::getName));
        File soundsDir = new File(packFolder, "assets/movietheatrecore/sounds");
        soundsDir.mkdirs();

        for (File entryFolder : entries) {
            File[] chunks = entryFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
            if (chunks == null) {
                continue;
            }
            Arrays.sort(chunks, Comparator.comparing(File::getName));
            File entryTarget = new File(soundsDir, entryFolder.getName());
            entryTarget.mkdirs();

            for (File chunk : chunks) {
                Files.copy(chunk.toPath(), new File(entryTarget, chunk.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                String chunkName = chunk.getName().replace(".ogg", "");
                Map<String, Object> soundDef = new HashMap<>();
                List<String> sounds = new ArrayList<>();
                sounds.add("movietheatrecore:" + entryFolder.getName() + "/" + chunkName);
                soundDef.put("sounds", sounds);
                soundsMap.put("movietheatrecore." + entryFolder.getName() + "." + chunkName, soundDef);
            }
        }
    }

    private void writeSoundsJson(File packFolder, Map<String, Object> soundsMap) throws IOException {
        File soundsJson = new File(packFolder, "assets/movietheatrecore/sounds.json");
        soundsJson.getParentFile().mkdirs();
        try (Writer writer = new FileWriter(soundsJson)) {
            new Gson().toJson(soundsMap, writer);
        }
    }

    private String resolvePackUrl() {
        String baseUrl = resolvePackBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + "pack.zip";
    }

    private String resolvePackBaseUrl() {
        if (configuration.resourcepack_server_enabled()) {
            if (!packServer.isRunning()) {
                packServer.start();
            }
            if (packServer.isRunning()) {
                return packServer.getPublicBaseUrl();
            }
        }
        String host = configuration.resourcepack_host_url();
        if (host != null && !host.isBlank()) {
            return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        }
        return null;
    }

    private void extractChunks(File mediaFile, File chunkFolder) throws IOException {
        String codec = configuration.audio_codec();
        String codecArg = codec.equalsIgnoreCase("vorbis") ? "libvorbis" : codec;
        String output = new File(chunkFolder, "chunk_%03d.ogg").getAbsolutePath();

        String[] command = {
                plugin.getFfmpeg().getExecutablePath(),
                "-hide_banner",
                "-loglevel", "error",
                "-i", mediaFile.getAbsolutePath(),
                "-vn",
                "-c:a", codecArg,
                "-ar", String.valueOf(configuration.audio_sample_rate()),
                "-f", "segment",
                "-segment_time", String.valueOf(configuration.audio_chunk_seconds()),
                "-reset_timestamps", "1",
                output
        };

        try {
            Process process = new ProcessBuilder(command).start();
            plugin.getProcess().add(process);
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Audio extraction interrupted.", e);
        }
    }

    private int countChunks(MediaEntry entry) {
        File chunkFolder = getAudioChunksFolder(entry);
        File[] chunks = chunkFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
        return chunks == null ? 0 : chunks.length;
    }

    private String computeAssetsHash() throws IOException {
        MessageDigest digest = getSha1Digest();
        File chunkRoot = configuration.getAudioChunksFolder();
        File[] entries = chunkRoot.listFiles(File::isDirectory);
        if (entries == null) {
            return "";
        }
        Arrays.sort(entries, Comparator.comparing(File::getName));
        for (File entryFolder : entries) {
            digest.update(entryFolder.getName().getBytes(StandardCharsets.UTF_8));
            File[] chunks = entryFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
            if (chunks == null) {
                continue;
            }
            Arrays.sort(chunks, Comparator.comparing(File::getName));
            for (File chunk : chunks) {
                digest.update(chunk.getName().getBytes(StandardCharsets.UTF_8));
                digest.update(Long.toString(chunk.length()).getBytes(StandardCharsets.UTF_8));
                digest.update(Long.toString(chunk.lastModified()).getBytes(StandardCharsets.UTF_8));
            }
        }
        return toHex(digest.digest());
    }

    private String computeChunkSignature(File chunkFolder) throws IOException {
        MessageDigest digest = getSha1Digest();
        File[] chunks = chunkFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
        if (chunks == null) {
            return "";
        }
        Arrays.sort(chunks, Comparator.comparing(File::getName));
        for (File chunk : chunks) {
            digest.update(chunk.getName().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(chunk.length()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(chunk.lastModified()).getBytes(StandardCharsets.UTF_8));
        }
        return toHex(digest.digest());
    }

    private MessageDigest getSha1Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 not available.", e);
        }
    }

    private String computeSha1(File file) throws IOException {
        MessageDigest digest = getSha1Digest();
        byte[] data = Files.readAllBytes(file.toPath());
        byte[] hashed = digest.digest(data);
        return toHex(hashed);
    }

    private String toHex(byte[] hashed) {
        StringBuilder builder = new StringBuilder();
        for (byte b : hashed) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private byte[] decodeSha1(String sha1) {
        if (sha1 == null || sha1.isEmpty()) {
            return new byte[0];
        }
        int len = sha1.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(sha1.charAt(i), 16) << 4)
                    + Character.digit(sha1.charAt(i + 1), 16));
        }
        return data;
    }
}
