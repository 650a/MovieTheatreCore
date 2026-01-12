package fr.xxathyx.mediaplayer.audio;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.configuration.Configuration;
import fr.xxathyx.mediaplayer.media.MediaEntry;
import fr.xxathyx.mediaplayer.resourcepack.ResourcePack;
import fr.xxathyx.mediaplayer.server.Server;

public class AudioPackManager {

    private final Main plugin;
    private final Configuration configuration;
    private final Map<String, Server> servers = new ConcurrentHashMap<>();
    private boolean warnedMissingHostUrl = false;

    public AudioPackManager(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
    }

    public AudioTrack prepare(MediaEntry entry, File mediaFile) throws IOException {
        String host = configuration.resourcepack_host_url();
        if (host == null || host.isBlank()) {
            if (!warnedMissingHostUrl) {
                plugin.getLogger().warning("[MediaPlayer]: audio enabled but no pack host-url configured");
                warnedMissingHostUrl = true;
            }
            return null;
        }
        File packFile = getPackFile(entry);
        if (!packFile.exists() || entry.getAudioSha1() == null || entry.getAudioSha1().isEmpty()) {
            buildPack(entry, mediaFile);
        }

        int chunkCount = entry.getAudioChunks();
        if (chunkCount <= 0) {
            chunkCount = countChunks(entry);
        }

        byte[] sha1 = decodeSha1(entry.getAudioSha1());
        String packUrl = resolvePackUrl(entry, packFile);
        if (packUrl == null || packUrl.isBlank()) {
            return null;
        }
        return new AudioTrack(entry.getId(), chunkCount, configuration.audio_chunk_seconds(), packUrl, sha1);
    }

    public void stopAll() {
        for (Server server : servers.values()) {
            server.stop();
        }
        servers.clear();
    }

    private File getPackFolder(MediaEntry entry) {
        return new File(configuration.getResourcePackFolder(), entry.getId());
    }

    private File getPackFile(MediaEntry entry) {
        return new File(configuration.getResourcePackFolder(), entry.getId() + ".zip");
    }

    private File getAudioChunksFolder(MediaEntry entry) {
        return new File(configuration.getAudioChunksFolder(), entry.getId());
    }

    private void buildPack(MediaEntry entry, File mediaFile) throws IOException {
        File chunkFolder = getAudioChunksFolder(entry);
        if (chunkFolder.exists()) {
            FileUtils.deleteDirectory(chunkFolder);
        }
        chunkFolder.mkdirs();

        extractChunks(mediaFile, chunkFolder);

        File packFolder = getPackFolder(entry);
        if (packFolder.exists()) {
            FileUtils.deleteDirectory(packFolder);
        }
        packFolder.mkdirs();

        createPackMetadata(packFolder, entry);
        copyChunksToPack(chunkFolder, packFolder, entry);
        int chunks = writeSoundsJson(packFolder, entry);

        File tempZip = new File(configuration.getResourcePackFolder(), entry.getId() + ".zip.tmp");
        File finalZip = getPackFile(entry);
        dev.jeka.core.api.file.JkPathTree.of(packFolder.toPath()).zipTo(tempZip.toPath());
        Files.move(tempZip.toPath(), finalZip.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        String sha1 = computeSha1(finalZip);
        entry.setAudioSha1(sha1);
        entry.setAudioChunks(chunks);
        configuration.set_resourcepack_sha1(sha1);
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

    private void createPackMetadata(File packFolder, MediaEntry entry) throws IOException {
        File metaFile = new File(packFolder, "pack.mcmeta");
        Map<String, Object> pack = new HashMap<>();
        pack.put("pack_format", new ResourcePack().getResourcePackFormat());
        pack.put("description", "MediaPlayer audio for " + entry.getName());

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

    private void copyChunksToPack(File chunkFolder, File packFolder, MediaEntry entry) throws IOException {
        File soundsDir = new File(packFolder, "assets/mediaplayer/sounds/" + entry.getId());
        soundsDir.mkdirs();
        File[] chunks = chunkFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
        if (chunks == null) {
            return;
        }
        for (File chunk : chunks) {
            Files.copy(chunk.toPath(), new File(soundsDir, chunk.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private int writeSoundsJson(File packFolder, MediaEntry entry) throws IOException {
        File soundsJson = new File(packFolder, "assets/mediaplayer/sounds.json");
        File chunkFolder = getAudioChunksFolder(entry);
        File[] chunks = chunkFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
        if (chunks == null) {
            return 0;
        }

        Map<String, Object> soundsMap = new HashMap<>();
        int count = 0;

        for (File chunk : chunks) {
            String chunkName = chunk.getName().replace(".ogg", "");
            Map<String, Object> soundDef = new HashMap<>();
            List<String> sounds = new ArrayList<>();
            sounds.add("mediaplayer:" + entry.getId() + "/" + chunkName);
            soundDef.put("sounds", sounds);
            soundsMap.put("mediaplayer." + entry.getId() + "." + chunkName, soundDef);
            count++;
        }

        try (Writer writer = new FileWriter(soundsJson)) {
            new Gson().toJson(soundsMap, writer);
        }

        return count;
    }

    private int countChunks(MediaEntry entry) {
        File chunkFolder = getAudioChunksFolder(entry);
        File[] chunks = chunkFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
        return chunks == null ? 0 : chunks.length;
    }

    private String resolvePackUrl(MediaEntry entry, File packFile) {
        String host = configuration.resourcepack_host_url();
        if (host != null && !host.isBlank()) {
            String url = host;
            if (url.contains("%media%")) {
                url = url.replace("%media%", entry.getId());
            } else if (url.contains("%name%")) {
                url = url.replace("%name%", entry.getName());
            } else {
                if (!url.endsWith("/")) {
                    url += "/";
                }
                url += entry.getId() + ".zip";
            }
            return url;
        }
        return null;
    }

    private String computeSha1(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] data = Files.readAllBytes(file.toPath());
            byte[] hashed = digest.digest(data);
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 not available.", e);
        }
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
