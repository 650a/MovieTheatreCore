package fr.xxathyx.mediaplayer.dependency;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.bukkit.Bukkit;

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.configuration.Configuration;

public class DependencyManager {

    public enum BinaryType {
        FFMPEG("ffmpeg", List.of("-version")),
        FFPROBE("ffprobe", List.of("-version")),
        YT_DLP("yt-dlp", List.of("--version")),
        DENO("deno", List.of("--version"));

        private final String commandName;
        private final List<String> versionArgs;

        BinaryType(String commandName, List<String> versionArgs) {
            this.commandName = commandName;
            this.versionArgs = versionArgs;
        }

        public String commandName() {
            return commandName;
        }

        public List<String> versionArgs() {
            return versionArgs;
        }
    }

    public static class ResolvedBinary {
        private final BinaryType type;
        private final Path sourcePath;
        private final Path stagedPath;
        private final String version;
        private final boolean valid;
        private final String error;

        ResolvedBinary(BinaryType type, Path sourcePath, Path stagedPath, String version, boolean valid, String error) {
            this.type = type;
            this.sourcePath = sourcePath;
            this.stagedPath = stagedPath;
            this.version = version;
            this.valid = valid;
            this.error = error;
        }

        public BinaryType getType() {
            return type;
        }

        public Path getSourcePath() {
            return sourcePath;
        }

        public Path getStagedPath() {
            return stagedPath;
        }

        public String getVersion() {
            return version;
        }

        public boolean isValid() {
            return valid;
        }

        public String getError() {
            return error;
        }
    }

    public static class EnvironmentInfo {
        private final String os;
        private final String arch;
        private final boolean pluginDirExecutable;
        private final boolean rootReadOnly;
        private final boolean stagingExecutable;
        private final boolean stagingWritable;
        private final Path stagingDir;

        EnvironmentInfo(String os, String arch, boolean pluginDirExecutable, boolean rootReadOnly,
                        boolean stagingExecutable, boolean stagingWritable, Path stagingDir) {
            this.os = os;
            this.arch = arch;
            this.pluginDirExecutable = pluginDirExecutable;
            this.rootReadOnly = rootReadOnly;
            this.stagingExecutable = stagingExecutable;
            this.stagingWritable = stagingWritable;
            this.stagingDir = stagingDir;
        }

        public String getOs() {
            return os;
        }

        public String getArch() {
            return arch;
        }

        public boolean isPluginDirExecutable() {
            return pluginDirExecutable;
        }

        public boolean isRootReadOnly() {
            return rootReadOnly;
        }

        public boolean isStagingExecutable() {
            return stagingExecutable;
        }

        public boolean isStagingWritable() {
            return stagingWritable;
        }

        public Path getStagingDir() {
            return stagingDir;
        }
    }

    private final Main plugin;
    private final Configuration configuration;
    private final Path pluginDir;
    private final Path cacheDir;
    private final Path binDir;
    private final Path stagingDir;
    private final Map<BinaryType, ResolvedBinary> resolvedCache = new ConcurrentHashMap<>();
    private volatile EnvironmentInfo environmentInfo;

    public DependencyManager(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
        this.pluginDir = plugin.getDataFolder().toPath();
        this.cacheDir = pluginDir.resolve("bin_cache");
        this.binDir = pluginDir.resolve("bin");
        this.stagingDir = Paths.get(System.getProperty("java.io.tmpdir"), "mediaplayer", "bin");
        refreshEnvironment();
    }

    public void refreshEnvironment() {
        boolean pluginExec = canExecuteInDir(pluginDir);
        boolean rootReadOnly = isRootReadOnly();
        boolean stagingWritable = ensureDirectory(stagingDir) && Files.isWritable(stagingDir);
        boolean stagingExecutable = canExecuteInDir(stagingDir);
        environmentInfo = new EnvironmentInfo(detectOs(), detectArch(), pluginExec, rootReadOnly, stagingExecutable, stagingWritable, stagingDir);
    }

    public EnvironmentInfo getEnvironmentInfo() {
        return environmentInfo;
    }

    public String getExecutablePath(BinaryType type) {
        ResolvedBinary resolved = resolveBinary(type, true);
        if (resolved != null && resolved.isValid() && resolved.getStagedPath() != null) {
            return resolved.getStagedPath().toString();
        }
        String configured = configuredPath(type);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return type.commandName();
    }

    public File getExecutableFile(BinaryType type) {
        ResolvedBinary resolved = resolveBinary(type, true);
        if (resolved != null && resolved.isValid() && resolved.getStagedPath() != null) {
            return resolved.getStagedPath().toFile();
        }
        File fallback = new File(getExecutablePath(type));
        return fallback.exists() ? fallback : null;
    }

    public boolean isAvailable(BinaryType type) {
        ResolvedBinary resolved = resolveBinary(type, true);
        if (resolved != null) {
            return resolved.isValid();
        }
        String command = getExecutablePath(type);
        return command != null && !command.isBlank();
    }

    public ResolvedBinary resolveBinary(BinaryType type, boolean allowDownload) {
        ResolvedBinary cached = resolvedCache.get(type);
        if (cached != null && cached.isValid() && cached.getStagedPath() != null && Files.exists(cached.getStagedPath())) {
            return cached;
        }

        refreshEnvironment();
        if (!environmentInfo.isStagingWritable() || !environmentInfo.isStagingExecutable()) {
            String error = "Staging directory is not executable or writable: " + stagingDir;
            Bukkit.getLogger().severe("[MediaPlayer]: " + error + " (set an executable /tmp or adjust java.io.tmpdir).");
            ResolvedBinary failed = new ResolvedBinary(type, null, null, null, false, error);
            resolvedCache.put(type, failed);
            return failed;
        }

        List<Path> candidates = new ArrayList<>();
        String configured = configuredPath(type);
        if (configured != null && !configured.isBlank()) {
            Optional<Path> resolved = resolveConfiguredPath(configured);
            resolved.ifPresent(candidates::add);
        }

        addCandidate(candidates, cacheDir.resolve(binaryFileName(type)));
        addCandidate(candidates, binDir.resolve(binaryFileName(type)));
        addCandidate(candidates, pluginDir.resolve("libraries").resolve(binaryFileName(type)));
        addCandidate(candidates, stagingDir.resolve(binaryFileName(type)));

        for (Path candidate : candidates) {
            ResolvedBinary resolved = validateAndStage(type, candidate);
            if (resolved != null && resolved.isValid()) {
                resolvedCache.put(type, resolved);
                return resolved;
            }
        }

        if (allowDownload && configuration.plugin_auto_update_libraries()) {
            ResolvedBinary downloaded = downloadAndStage(type);
            resolvedCache.put(type, downloaded);
            return downloaded;
        }

        ResolvedBinary failed = new ResolvedBinary(type, null, null, null, false, "Binary not available");
        resolvedCache.put(type, failed);
        return failed;
    }

    public void warmUpDependenciesAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            resolveBinary(BinaryType.FFMPEG, true);
            resolveBinary(BinaryType.FFPROBE, true);
        });
    }

    private void addCandidate(List<Path> candidates, Path candidate) {
        if (candidate != null && Files.exists(candidate)) {
            candidates.add(candidate);
        }
    }

    private String configuredPath(BinaryType type) {
        return switch (type) {
            case FFMPEG -> configuration.sources_ffmpeg_path();
            case FFPROBE -> configuration.sources_ffprobe_path();
            case YT_DLP -> configuration.media_youtube_resolver_path();
            case DENO -> configuration.sources_deno_path();
        };
    }

    private Optional<Path> resolveConfiguredPath(String configured) {
        Path candidate = Paths.get(configured);
        if (candidate.isAbsolute() || configured.contains("/") || configured.contains("\\")) {
            if (Files.exists(candidate)) {
                return Optional.of(candidate);
            }
            return Optional.empty();
        }
        return resolveOnPath(configured);
    }

    private Optional<Path> resolveOnPath(String command) {
        String resolver = detectOs().startsWith("windows") ? "where" : "which";
        try {
            Process process = new ProcessBuilder(resolver, command).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            process.waitFor();
            if (!output.isBlank()) {
                String firstLine = output.split("\r?\n")[0].trim();
                if (!firstLine.isBlank()) {
                    return Optional.of(Paths.get(firstLine));
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return Optional.empty();
    }

    private ResolvedBinary validateAndStage(BinaryType type, Path candidate) {
        try {
            if (!Files.exists(candidate) || Files.size(candidate) < minimumSize(type)) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }

        Path staged = stageBinary(candidate, type);
        if (staged == null) {
            return null;
        }
        String version = probeVersion(staged, type);
        if (version == null) {
            return new ResolvedBinary(type, candidate, staged, null, false, "Failed version check");
        }
        return new ResolvedBinary(type, candidate, staged, version, true, null);
    }

    private Path stageBinary(Path source, BinaryType type) {
        try {
            ensureDirectory(stagingDir);
            Path target = stagingDir.resolve(binaryFileName(type));
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            makeExecutable(target);
            return target;
        } catch (IOException e) {
            return null;
        }
    }

    private ResolvedBinary downloadAndStage(BinaryType type) {
        try {
            ensureDirectory(cacheDir);
            return switch (type) {
                case FFMPEG, FFPROBE -> downloadFfmpegBundle(type);
                case YT_DLP -> downloadYtDlp();
                case DENO -> downloadDeno();
            };
        } catch (IOException e) {
            Bukkit.getLogger().warning("[MediaPlayer]: Failed to prepare " + type.commandName()
                    + " binary (no URL available): " + e.getMessage());
            return new ResolvedBinary(type, null, null, null, false, "Download failed: " + e.getMessage());
        }
    }

    private ResolvedBinary downloadYtDlp() throws IOException {
        String os = detectOs();
        String arch = detectArch();
        String url;
        String fileName;
        if (os.startsWith("windows")) {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
            fileName = "yt-dlp.exe";
        } else if (os.startsWith("mac")) {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos";
            fileName = "yt-dlp";
        } else {
            if ("aarch64".equals(arch)) {
                url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64";
            } else {
                url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux";
            }
            fileName = "yt-dlp";
        }
        Path target = cacheDir.resolve(fileName);
        try {
            downloadFile(url, target);
            makeExecutable(target);
            return validateAndStage(BinaryType.YT_DLP, target);
        } catch (IOException e) {
            logDownloadFailure(BinaryType.YT_DLP, url, e);
            return new ResolvedBinary(BinaryType.YT_DLP, null, null, null, false, "Download failed: " + e.getMessage());
        }
    }

    private ResolvedBinary downloadDeno() throws IOException {
        String os = detectOs();
        String arch = detectArch();
        String url;
        if (os.startsWith("windows")) {
            url = "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip";
        } else if (os.startsWith("mac")) {
            url = "https://github.com/denoland/deno/releases/latest/download/deno-" + ("aarch64".equals(arch) ? "aarch64" : "x86_64") + "-apple-darwin.zip";
        } else {
            url = "https://github.com/denoland/deno/releases/latest/download/deno-" + ("aarch64".equals(arch) ? "aarch64" : "x86_64") + "-unknown-linux-gnu.zip";
        }
        Path archive = cacheDir.resolve("deno.zip");
        try {
            downloadFile(url, archive);
            extractZip(archive, cacheDir, new BundleSpec(new BundleFile("deno", binaryFileName(BinaryType.DENO))));
            Path binary = cacheDir.resolve(binaryFileName(BinaryType.DENO));
            makeExecutable(binary);
            return validateAndStage(BinaryType.DENO, binary);
        } catch (IOException e) {
            logDownloadFailure(BinaryType.DENO, url, e);
            return new ResolvedBinary(BinaryType.DENO, null, null, null, false, "Download failed: " + e.getMessage());
        }
    }

    private String probeVersion(Path executable, BinaryType type) {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(type.versionArgs());
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            process.waitFor();
            if (process.exitValue() != 0 || output.isBlank()) {
                return null;
            }
            String[] lines = output.split("\r?\n");
            return lines.length > 0 ? lines[0].trim() : output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private boolean ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void makeExecutable(Path file) {
        if (file == null) {
            return;
        }
        try {
            file.toFile().setExecutable(true, false);
            if (!detectOs().startsWith("windows")) {
                Set<PosixFilePermission> perms = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE
                );
                Files.setPosixFilePermissions(file, perms);
            }
        } catch (IOException ignored) {
        }
    }

    private boolean canExecuteInDir(Path dir) {
        if (!ensureDirectory(dir)) {
            return false;
        }
        String os = detectOs();
        Path script = dir.resolve("mp_exec_test_" + Instant.now().toEpochMilli());
        try {
            if (os.startsWith("windows")) {
                Path bat = Paths.get(script.toString() + ".bat");
                try (BufferedWriter writer = Files.newBufferedWriter(bat, StandardCharsets.UTF_8)) {
                    writer.write("@echo off\r\n");
                    writer.write("echo ok\r\n");
                }
                Process process = new ProcessBuilder("cmd.exe", "/c", bat.toString()).start();
                process.waitFor();
                Files.deleteIfExists(bat);
                return process.exitValue() == 0;
            }
            try (BufferedWriter writer = Files.newBufferedWriter(script, StandardCharsets.UTF_8)) {
                writer.write("#!/bin/sh\n");
                writer.write("echo ok\n");
            }
            makeExecutable(script);
            Process process = new ProcessBuilder(script.toString()).start();
            process.waitFor();
            Files.deleteIfExists(script);
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private boolean isRootReadOnly() {
        if (!detectOs().startsWith("linux")) {
            return false;
        }
        Path mounts = Paths.get("/proc/mounts");
        if (!Files.exists(mounts)) {
            return false;
        }
        try (BufferedReader reader = Files.newBufferedReader(mounts, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 4 && "/".equals(parts[1])) {
                    String options = parts[3];
                    for (String option : options.split(",")) {
                        if ("ro".equals(option)) {
                            return true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private String detectOs() {
        return System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
    }

    private String detectArch() {
        String arch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        }
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            return "x86_64";
        }
        return arch;
    }

    private String binaryFileName(BinaryType type) {
        boolean windows = detectOs().startsWith("windows");
        String base = type.commandName();
        if (windows) {
            return base + ".exe";
        }
        return base;
    }

    private void downloadFile(String url, Path target) throws IOException {
        try (InputStream input = new BufferedInputStream(new URL(url).openStream())) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private long minimumSize(BinaryType type) {
        return switch (type) {
            case FFMPEG, FFPROBE -> 1_000_000L;
            case YT_DLP -> 100_000L;
            case DENO -> 1_000_000L;
        };
    }

    private void extractTarXz(Path archive, Path destination, BundleSpec bundle) throws IOException {
        try (InputStream fileInput = Files.newInputStream(archive);
             BufferedInputStream buffered = new BufferedInputStream(fileInput);
             XZCompressorInputStream xz = new XZCompressorInputStream(buffered);
             TarArchiveInputStream tar = new TarArchiveInputStream(xz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                for (BundleFile file : bundle.files) {
                    if (name.endsWith("/" + file.archiveName)) {
                        Path out = destination.resolve(file.targetName);
                        try (OutputStream output = Files.newOutputStream(out)) {
                            tar.transferTo(output);
                        }
                    }
                }
            }
        }
    }

    private void extractZip(Path archive, Path destination, BundleSpec bundle) throws IOException {
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                for (BundleFile file : bundle.files) {
                    if (name.endsWith("/" + file.archiveName) || name.equals(file.archiveName)) {
                        Path out = destination.resolve(file.targetName);
                        try (OutputStream output = Files.newOutputStream(out)) {
                            zip.transferTo(output);
                        }
                    }
                }
            }
        }
    }

    private FfmpegBundle resolveFfmpegBundle(String os, String arch) {
        if (os.startsWith("windows")) {
            return new FfmpegBundle("https://github.com/yt-dlp/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-win64-gpl.zip",
                    ArchiveFormat.ZIP, "win64.zip",
                    new BundleSpec(new BundleFile("bin/ffmpeg.exe", "ffmpeg.exe"), new BundleFile("bin/ffprobe.exe", "ffprobe.exe")));
        }
        if (os.startsWith("mac")) {
            String suffix = "aarch64".equals(arch) ? "macosarm64" : "macos64";
            return new FfmpegBundle("https://github.com/yt-dlp/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-" + suffix + "-gpl.zip",
                    ArchiveFormat.ZIP, suffix + ".zip",
                    new BundleSpec(new BundleFile("bin/ffmpeg", "ffmpeg"), new BundleFile("bin/ffprobe", "ffprobe")));
        }
        if (os.startsWith("linux")) {
            String suffix = "aarch64".equals(arch) ? "linuxarm64" : "linux64";
            return new FfmpegBundle("https://github.com/yt-dlp/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-" + suffix + "-gpl.tar.xz",
                    ArchiveFormat.TAR_XZ, suffix + ".tar.xz",
                    new BundleSpec(new BundleFile("ffmpeg", "ffmpeg"), new BundleFile("ffprobe", "ffprobe")));
        }
        return null;
    }

    private static class BundleSpec {
        private final List<BundleFile> files;

        BundleSpec(BundleFile... files) {
            this.files = List.of(files);
        }
    }

    private static class BundleFile {
        private final String archiveName;
        private final String targetName;

        BundleFile(String archiveName, String targetName) {
            this.archiveName = archiveName;
            this.targetName = targetName;
        }
    }

    private enum ArchiveFormat {
        TAR_XZ,
        ZIP
    }

    private static class FfmpegBundle {
        private final String url;
        private final ArchiveFormat format;
        private final String archiveSuffix;
        private final BundleSpec bundle;

        FfmpegBundle(String url, ArchiveFormat format, String archiveSuffix, BundleSpec bundle) {
            this.url = url;
            this.format = format;
            this.archiveSuffix = archiveSuffix;
            this.bundle = bundle;
        }
    }

    private ResolvedBinary downloadFfmpegBundle(BinaryType type) throws IOException {
        FfmpegBundle bundle = resolveFfmpegBundle(detectOs(), detectArch());
        if (bundle == null) {
            return new ResolvedBinary(type, null, null, null, false, "Unsupported OS/arch for ffmpeg bundle");
        }
        Path archivePath = cacheDir.resolve("ffmpeg-" + bundle.archiveSuffix);
        try {
            downloadFile(bundle.url, archivePath);
            if (bundle.format == ArchiveFormat.TAR_XZ) {
                extractTarXz(archivePath, cacheDir, bundle.bundle);
            } else {
                extractZip(archivePath, cacheDir, bundle.bundle);
            }
        } catch (IOException e) {
            logDownloadFailure(type, bundle.url, e);
            return new ResolvedBinary(type, null, null, null, false, "Download failed: " + e.getMessage());
        }
        Path binary = cacheDir.resolve(binaryFileName(type));
        if (!Files.exists(binary)) {
            return new ResolvedBinary(type, null, null, null, false, "Downloaded ffmpeg bundle missing " + binaryFileName(type));
        }
        makeExecutable(binary);
        return validateAndStage(type, binary);
    }

    private void logDownloadFailure(BinaryType type, String url, IOException error) {
        Bukkit.getLogger().warning("[MediaPlayer]: Failed to download/extract " + type.commandName()
                + " from " + url + " (" + error.getMessage() + "). Feature will be disabled.");
    }
}
