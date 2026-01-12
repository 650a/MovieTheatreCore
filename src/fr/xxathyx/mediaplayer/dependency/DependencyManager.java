package fr.xxathyx.mediaplayer.dependency;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    public enum BinarySource {
        CONFIG,
        SYSTEM,
        CACHE
    }

    public static class ResolvedBinary {
        private final BinaryType type;
        private final Path path;
        private final BinarySource source;
        private final String version;
        private final boolean valid;
        private final String error;

        ResolvedBinary(BinaryType type, Path path, BinarySource source, String version, boolean valid, String error) {
            this.type = type;
            this.path = path;
            this.source = source;
            this.version = version;
            this.valid = valid;
            this.error = error;
        }

        public BinaryType getType() {
            return type;
        }

        public Path getPath() {
            return path;
        }

        public BinarySource getSource() {
            return source;
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
        private final boolean installDirExecutable;
        private final boolean installDirWritable;
        private final Path installDir;

        EnvironmentInfo(String os, String arch, boolean pluginDirExecutable, boolean rootReadOnly,
                        boolean installDirExecutable, boolean installDirWritable, Path installDir) {
            this.os = os;
            this.arch = arch;
            this.pluginDirExecutable = pluginDirExecutable;
            this.rootReadOnly = rootReadOnly;
            this.installDirExecutable = installDirExecutable;
            this.installDirWritable = installDirWritable;
            this.installDir = installDir;
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

        public boolean isInstallDirExecutable() {
            return installDirExecutable;
        }

        public boolean isInstallDirWritable() {
            return installDirWritable;
        }

        public Path getInstallDir() {
            return installDir;
        }
    }

    private static final long DEFAULT_DOWNLOAD_TIMEOUT_MS = 30_000;
    private static final long MAX_DOWNLOAD_YT_DLP = 50L * 1024L * 1024L;
    private static final long MAX_DOWNLOAD_DENO = 150L * 1024L * 1024L;
    private static final long MAX_DOWNLOAD_FFMPEG = 350L * 1024L * 1024L;

    private final Main plugin;
    private final Configuration configuration;
    private final Path pluginDir;
    private final Path tmpInstallDir;
    private final Path pluginBinDir;
    private final Path stateFile;
    private final Map<BinaryType, ResolvedBinary> resolvedCache = new ConcurrentHashMap<>();
    private final Map<BinaryType, String> loggedVersions = new ConcurrentHashMap<>();
    private final Properties stateProperties = new Properties();
    private volatile EnvironmentInfo environmentInfo;
    private volatile boolean environmentLogged;

    public DependencyManager(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
        this.pluginDir = plugin.getDataFolder().toPath();
        this.tmpInstallDir = Paths.get("/tmp", "mediaplayer", "bin");
        this.pluginBinDir = pluginDir.resolve("bin");
        this.stateFile = pluginDir.resolve("dependency-state.properties");
        loadState();
        refreshEnvironment();
    }

    public void refreshEnvironment() {
        boolean pluginExec = canExecuteInDir(pluginDir);
        boolean rootReadOnly = isRootReadOnly();
        Path installDir = selectInstallDir();
        boolean installWritable = ensureDirectory(installDir) && Files.isWritable(installDir);
        boolean installExecutable = canExecuteInDir(installDir);
        environmentInfo = new EnvironmentInfo(detectOs(), detectArch(), pluginExec, rootReadOnly,
                installExecutable, installWritable, installDir);

        if (!environmentLogged) {
            environmentLogged = true;
            Bukkit.getLogger().info("[MediaPlayer]: Detected OS/arch: " + environmentInfo.getOs() + "/" + environmentInfo.getArch());
            Bukkit.getLogger().info("[MediaPlayer]: Plugin dir executable: " + yesNo(environmentInfo.isPluginDirExecutable()));
            Bukkit.getLogger().info("[MediaPlayer]: Install dir: " + environmentInfo.getInstallDir());
            Bukkit.getLogger().info("[MediaPlayer]: Install dir writable: " + yesNo(environmentInfo.isInstallDirWritable())
                    + ", executable: " + yesNo(environmentInfo.isInstallDirExecutable()));
        }
    }

    public EnvironmentInfo getEnvironmentInfo() {
        return environmentInfo;
    }

    public String getExecutablePath(BinaryType type) {
        ResolvedBinary resolved = resolveBinary(type, true);
        if (resolved != null && resolved.isValid() && resolved.getPath() != null) {
            return resolved.getPath().toString();
        }
        String configured = configuredPath(type);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return type.commandName();
    }

    public File getExecutableFile(BinaryType type) {
        ResolvedBinary resolved = resolveBinary(type, true);
        if (resolved != null && resolved.isValid() && resolved.getPath() != null) {
            return resolved.getPath().toFile();
        }
        File fallback = new File(getExecutablePath(type));
        return fallback.exists() ? fallback : null;
    }

    public boolean isAvailable(BinaryType type) {
        ResolvedBinary resolved = resolveBinary(type, false);
        return resolved != null && resolved.isValid();
    }

    public ResolvedBinary resolveBinary(BinaryType type, boolean allowDownload) {
        return resolveBinary(type, allowDownload, false, false);
    }

    public ResolvedBinary resolveBinary(BinaryType type, boolean allowDownload, boolean forceDownload, boolean ignoreSystem) {
        if (type == BinaryType.FFMPEG || type == BinaryType.FFPROBE) {
            resolveFfmpegPair(allowDownload, forceDownload, ignoreSystem);
            return resolvedCache.get(type);
        }
        return resolveSingleBinary(type, allowDownload, forceDownload, ignoreSystem);
    }

    public void warmUpDependenciesAsync() {
        if (!configuration.dependencies_install_auto_install()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            resolveBinary(BinaryType.FFMPEG, true);
            resolveBinary(BinaryType.FFPROBE, true);
            resolveBinary(BinaryType.YT_DLP, true);
            resolveBinary(BinaryType.DENO, true);
        });
    }

    public void reinstallAllAsync(Runnable onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            resolveBinary(BinaryType.FFMPEG, true, true, true);
            resolveBinary(BinaryType.FFPROBE, true, true, true);
            resolveBinary(BinaryType.YT_DLP, true, true, true);
            resolveBinary(BinaryType.DENO, true, true, true);
            if (onComplete != null) {
                Bukkit.getScheduler().runTask(plugin, onComplete);
            }
        });
    }

    private ResolvedBinary resolveSingleBinary(BinaryType type, boolean allowDownload, boolean forceDownload, boolean ignoreSystem) {
        ResolvedBinary cached = resolvedCache.get(type);
        if (cached != null && cached.isValid() && cached.getPath() != null && Files.exists(cached.getPath())) {
            if (shouldAutoUpdate(type, allowDownload) && cached.getSource() == BinarySource.CACHE) {
                return attemptAutoUpdate(type, cached, forceDownload, ignoreSystem);
            }
            return cached;
        }

        refreshEnvironment();
        if (!environmentInfo.isInstallDirWritable() || !environmentInfo.isInstallDirExecutable()) {
            String error = "Install directory is not executable or writable: " + environmentInfo.getInstallDir();
            Bukkit.getLogger().severe("[MediaPlayer]: " + error + " (set an executable /tmp or adjust dependencies.install.directory)." );
            ResolvedBinary failed = new ResolvedBinary(type, null, null, null, false, error);
            resolvedCache.put(type, failed);
            return failed;
        }

        List<Candidate> candidates = buildCandidates(type, ignoreSystem);
        for (Candidate candidate : candidates) {
            ResolvedBinary resolved = validateCandidate(type, candidate);
            if (resolved != null && resolved.isValid()) {
                resolvedCache.put(type, resolved);
                logResolved(resolved);
                if (shouldAutoUpdate(type, allowDownload) && resolved.getSource() == BinarySource.CACHE) {
                    return attemptAutoUpdate(type, resolved, forceDownload, ignoreSystem);
                }
                return resolved;
            }
        }

        if (allowDownload && configuration.dependencies_install_auto_install()) {
            return downloadBinary(type, forceDownload, ignoreSystem);
        }

        ResolvedBinary failed = new ResolvedBinary(type, null, null, null, false, "Binary not available");
        resolvedCache.put(type, failed);
        return failed;
    }

    private void resolveFfmpegPair(boolean allowDownload, boolean forceDownload, boolean ignoreSystem) {
        ResolvedBinary cachedFfmpeg = resolvedCache.get(BinaryType.FFMPEG);
        ResolvedBinary cachedProbe = resolvedCache.get(BinaryType.FFPROBE);
        if (cachedFfmpeg != null && cachedProbe != null
                && cachedFfmpeg.isValid() && cachedProbe.isValid()
                && cachedFfmpeg.getPath() != null && cachedProbe.getPath() != null
                && Files.exists(cachedFfmpeg.getPath()) && Files.exists(cachedProbe.getPath())) {
            if (shouldAutoUpdate(BinaryType.FFMPEG, allowDownload)
                    && cachedFfmpeg.getSource() == BinarySource.CACHE
                    && cachedProbe.getSource() == BinarySource.CACHE) {
                attemptFfmpegUpdate(cachedFfmpeg, cachedProbe, forceDownload, ignoreSystem);
            }
            return;
        }

        refreshEnvironment();
        List<Candidate> ffmpegCandidates = buildCandidates(BinaryType.FFMPEG, ignoreSystem);
        List<Candidate> ffprobeCandidates = buildCandidates(BinaryType.FFPROBE, ignoreSystem);

        ResolvedBinary configFfmpeg = firstValid(BinaryType.FFMPEG, ffmpegCandidates, BinarySource.CONFIG);
        ResolvedBinary configProbe = firstValid(BinaryType.FFPROBE, ffprobeCandidates, BinarySource.CONFIG);
        if (configFfmpeg != null && configProbe != null) {
            resolvedCache.put(BinaryType.FFMPEG, configFfmpeg);
            resolvedCache.put(BinaryType.FFPROBE, configProbe);
            logResolved(configFfmpeg);
            logResolved(configProbe);
            return;
        }

        ResolvedBinary systemFfmpeg = firstValid(BinaryType.FFMPEG, ffmpegCandidates, BinarySource.SYSTEM);
        ResolvedBinary systemProbe = firstValid(BinaryType.FFPROBE, ffprobeCandidates, BinarySource.SYSTEM);
        if (systemFfmpeg != null && systemProbe != null) {
            resolvedCache.put(BinaryType.FFMPEG, systemFfmpeg);
            resolvedCache.put(BinaryType.FFPROBE, systemProbe);
            logResolved(systemFfmpeg);
            logResolved(systemProbe);
            return;
        }

        ResolvedBinary cacheFfmpeg = firstValid(BinaryType.FFMPEG, ffmpegCandidates, BinarySource.CACHE);
        ResolvedBinary cacheProbe = firstValid(BinaryType.FFPROBE, ffprobeCandidates, BinarySource.CACHE);
        if (cacheFfmpeg != null && cacheProbe != null) {
            resolvedCache.put(BinaryType.FFMPEG, cacheFfmpeg);
            resolvedCache.put(BinaryType.FFPROBE, cacheProbe);
            logResolved(cacheFfmpeg);
            logResolved(cacheProbe);
            if (shouldAutoUpdate(BinaryType.FFMPEG, allowDownload)) {
                attemptFfmpegUpdate(cacheFfmpeg, cacheProbe, forceDownload, ignoreSystem);
            }
            return;
        }

        if (allowDownload && configuration.dependencies_install_auto_install()) {
            downloadFfmpegBundle(forceDownload, ignoreSystem, false);
            return;
        }

        ResolvedBinary fallbackFfmpeg = firstValid(BinaryType.FFMPEG, ffmpegCandidates, null);
        ResolvedBinary fallbackProbe = firstValid(BinaryType.FFPROBE, ffprobeCandidates, null);
        if (fallbackFfmpeg != null && fallbackProbe != null) {
            if (fallbackFfmpeg.getSource() == BinarySource.SYSTEM || fallbackProbe.getSource() == BinarySource.SYSTEM) {
                if (cacheFfmpeg != null && cacheProbe != null) {
                    resolvedCache.put(BinaryType.FFMPEG, cacheFfmpeg);
                    resolvedCache.put(BinaryType.FFPROBE, cacheProbe);
                    logResolved(cacheFfmpeg);
                    logResolved(cacheProbe);
                    return;
                }
            }
            resolvedCache.put(BinaryType.FFMPEG, fallbackFfmpeg);
            resolvedCache.put(BinaryType.FFPROBE, fallbackProbe);
            logResolved(fallbackFfmpeg);
            logResolved(fallbackProbe);
            return;
        }

        resolvedCache.put(BinaryType.FFMPEG, new ResolvedBinary(BinaryType.FFMPEG, null, null, null, false, "Binary not available"));
        resolvedCache.put(BinaryType.FFPROBE, new ResolvedBinary(BinaryType.FFPROBE, null, null, null, false, "Binary not available"));
    }

    private ResolvedBinary firstValid(BinaryType type, List<Candidate> candidates, BinarySource source) {
        for (Candidate candidate : candidates) {
            if (source != null && candidate.source != source) {
                continue;
            }
            ResolvedBinary resolved = validateCandidate(type, candidate);
            if (resolved != null && resolved.isValid()) {
                return resolved;
            }
        }
        return null;
    }

    private ResolvedBinary downloadBinary(BinaryType type, boolean forceDownload, boolean ignoreSystem) {
        if (!supportsDownloads()) {
            String error = "Automatic downloads are only supported on Linux.";
            ResolvedBinary failed = new ResolvedBinary(type, null, null, null, false, error);
            resolvedCache.put(type, failed);
            return failed;
        }
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> resolveBinary(type, true, forceDownload, ignoreSystem));
            ResolvedBinary failed = new ResolvedBinary(type, null, null, null, false, "Install scheduled");
            resolvedCache.put(type, failed);
            return failed;
        }

        if (!forceDownload && !ignoreSystem) {
            Candidate system = findSystemCandidate(type);
            if (system != null) {
                ResolvedBinary resolved = validateCandidate(type, system);
                if (resolved != null && resolved.isValid()) {
                    resolvedCache.put(type, resolved);
                    logResolved(resolved);
                    return resolved;
                }
            }
        }

        return switch (type) {
            case YT_DLP -> downloadYtDlp();
            case DENO -> downloadDeno();
            case FFMPEG, FFPROBE -> {
                downloadFfmpegBundle(forceDownload, ignoreSystem, false);
                yield resolvedCache.get(type);
            }
        };
    }

    private ResolvedBinary attemptAutoUpdate(BinaryType type, ResolvedBinary current, boolean forceDownload, boolean ignoreSystem) {
        ResolvedBinary updated = downloadBinary(type, forceDownload, ignoreSystem);
        if (updated != null && updated.isValid()) {
            return updated;
        }
        resolvedCache.put(type, current);
        return current;
    }

    private void attemptFfmpegUpdate(ResolvedBinary currentFfmpeg, ResolvedBinary currentProbe, boolean forceDownload, boolean ignoreSystem) {
        downloadFfmpegBundle(forceDownload, ignoreSystem, true);
        ResolvedBinary updatedFfmpeg = resolvedCache.get(BinaryType.FFMPEG);
        ResolvedBinary updatedProbe = resolvedCache.get(BinaryType.FFPROBE);
        if (updatedFfmpeg == null || updatedProbe == null || !updatedFfmpeg.isValid() || !updatedProbe.isValid()) {
            resolvedCache.put(BinaryType.FFMPEG, currentFfmpeg);
            resolvedCache.put(BinaryType.FFPROBE, currentProbe);
        }
    }

    private boolean shouldAutoUpdate(BinaryType type, boolean allowDownload) {
        if (!allowDownload) {
            return false;
        }
        if (!configuration.dependencies_install_auto_update()) {
            return false;
        }
        long hours = configuration.dependencies_install_update_check_hours();
        if (hours <= 0) {
            return false;
        }
        long lastCheck = getLastUpdateCheck(type);
        long now = System.currentTimeMillis();
        return now - lastCheck >= TimeUnit.HOURS.toMillis(hours);
    }

    private void markUpdateCheck(BinaryType type) {
        stateProperties.setProperty("lastUpdateCheck." + type.name().toLowerCase(Locale.ROOT), Long.toString(System.currentTimeMillis()));
        saveState();
    }

    private long getLastUpdateCheck(BinaryType type) {
        String value = stateProperties.getProperty("lastUpdateCheck." + type.name().toLowerCase(Locale.ROOT));
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void loadState() {
        if (!Files.exists(stateFile)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
            stateProperties.load(reader);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[MediaPlayer]: Failed to read dependency state: " + e.getMessage());
        }
    }

    private void saveState() {
        try {
            ensureDirectory(stateFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(stateFile, StandardCharsets.UTF_8)) {
                stateProperties.store(writer, "MediaPlayer dependency state");
            }
        } catch (IOException e) {
            Bukkit.getLogger().warning("[MediaPlayer]: Failed to write dependency state: " + e.getMessage());
        }
    }

    private Candidate findSystemCandidate(BinaryType type) {
        Optional<Path> resolved = resolveOnPath(type.commandName());
        if (resolved.isEmpty()) {
            return null;
        }
        return new Candidate(BinarySource.SYSTEM, resolved.get());
    }

    private List<Candidate> buildCandidates(BinaryType type, boolean ignoreSystem) {
        List<Candidate> candidates = new ArrayList<>();
        String configured = configuredPath(type);
        if (configured != null && !configured.isBlank()) {
            Optional<Path> resolved = resolveConfiguredPath(configured);
            resolved.ifPresent(path -> candidates.add(new Candidate(BinarySource.CONFIG, path)));
        }

        if (!ignoreSystem && preferSystem(type)) {
            Candidate system = findSystemCandidate(type);
            if (system != null) {
                candidates.add(system);
            }
        }

        Path cachePath = environmentInfo.getInstallDir().resolve(binaryFileName(type));
        if (Files.exists(cachePath)) {
            candidates.add(new Candidate(BinarySource.CACHE, cachePath));
        }

        return candidates;
    }

    private boolean preferSystem(BinaryType type) {
        return switch (type) {
            case FFMPEG -> configuration.dependencies_prefer_system_ffmpeg();
            case FFPROBE -> configuration.dependencies_prefer_system_ffprobe();
            case YT_DLP -> configuration.dependencies_prefer_system_ytdlp();
            case DENO -> configuration.dependencies_prefer_system_deno();
        };
    }

    private ResolvedBinary validateCandidate(BinaryType type, Candidate candidate) {
        if (candidate == null || candidate.path == null) {
            return null;
        }
        try {
            if (!Files.exists(candidate.path) || Files.isDirectory(candidate.path)) {
                return null;
            }
            if (Files.size(candidate.path) < minimumSize(type)) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }

        if (!Files.isExecutable(candidate.path)) {
            return null;
        }

        if (!validateArch(candidate.path)) {
            return null;
        }

        String version = probeVersion(candidate.path, type);
        if (version == null) {
            return new ResolvedBinary(type, candidate.path, candidate.source, null, false, "Failed version check");
        }
        return new ResolvedBinary(type, candidate.path, candidate.source, version, true, null);
    }

    private boolean validateArch(Path executable) {
        if (!detectOs().startsWith("linux")) {
            return true;
        }
        byte[] header = new byte[20];
        try (InputStream input = Files.newInputStream(executable)) {
            int read = input.read(header);
            if (read < header.length) {
                return false;
            }
            if (header[0] != 0x7F || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') {
                return true;
            }
            ByteBuffer buffer = ByteBuffer.wrap(header, 18, 2).order(ByteOrder.LITTLE_ENDIAN);
            int machine = buffer.getShort() & 0xFFFF;
            String arch = detectArch();
            if ("x86_64".equals(arch)) {
                return machine == 62;
            }
            if ("aarch64".equals(arch)) {
                return machine == 183;
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private String configuredPath(BinaryType type) {
        return switch (type) {
            case FFMPEG -> configuration.dependencies_path_ffmpeg();
            case FFPROBE -> configuration.dependencies_path_ffprobe();
            case YT_DLP -> configuration.dependencies_path_ytdlp();
            case DENO -> configuration.dependencies_path_deno();
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

    private String probeVersion(Path executable, BinaryType type) {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(type.versionArgs());
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
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

    private void logResolved(ResolvedBinary resolved) {
        if (resolved == null || !resolved.isValid()) {
            return;
        }
        String previous = loggedVersions.get(resolved.getType());
        String current = resolved.getVersion() + "|" + resolved.getSource() + "|" + resolved.getPath();
        if (current.equals(previous)) {
            return;
        }
        loggedVersions.put(resolved.getType(), current);
        Bukkit.getLogger().info("[MediaPlayer]: Using " + resolved.getType().commandName() + " (" + resolved.getSource().name().toLowerCase(Locale.ROOT)
                + ") at " + resolved.getPath() + " (" + resolved.getVersion() + ")");
    }

    private Path selectInstallDir() {
        String configured = configuration.dependencies_install_directory();
        if (configured != null && !configured.isBlank()) {
            Path configuredPath = Paths.get(configured);
            if (!configuredPath.isAbsolute()) {
                configuredPath = pluginDir.resolve(configuredPath);
            }
            if (canExecuteInDir(configuredPath)) {
                return configuredPath;
            }
            Bukkit.getLogger().warning("[MediaPlayer]: Configured install directory not executable: " + configuredPath + ". Falling back to /tmp.");
        }
        if (canExecuteInDir(tmpInstallDir)) {
            return tmpInstallDir;
        }
        if (canExecuteInDir(pluginBinDir)) {
            return pluginBinDir;
        }
        Bukkit.getLogger().warning("[MediaPlayer]: No executable install directory found. Falling back to /tmp.");
        return tmpInstallDir;
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

    private boolean supportsDownloads() {
        return detectOs().startsWith("linux") && ("x86_64".equals(detectArch()) || "aarch64".equals(detectArch()));
    }

    private String binaryFileName(BinaryType type) {
        boolean windows = detectOs().startsWith("windows");
        String base = type.commandName();
        if (windows) {
            return base + ".exe";
        }
        return base;
    }

    private long minimumSize(BinaryType type) {
        return switch (type) {
            case FFMPEG, FFPROBE -> 1_000_000L;
            case YT_DLP -> 100_000L;
            case DENO -> 1_000_000L;
        };
    }

    private ResolvedBinary downloadYtDlp() {
        String arch = detectArch();
        String url;
        if ("aarch64".equals(arch)) {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64";
        } else {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux";
        }
        Path target = environmentInfo.getInstallDir().resolve("yt-dlp");
        return downloadBinaryFile(BinaryType.YT_DLP, url, target, MAX_DOWNLOAD_YT_DLP);
    }

    private ResolvedBinary downloadDeno() {
        String arch = detectArch();
        String url = "https://github.com/denoland/deno/releases/latest/download/deno-" + ("aarch64".equals(arch) ? "aarch64" : "x86_64")
                + "-unknown-linux-gnu.zip";
        Path archive = environmentInfo.getInstallDir().resolve("deno.zip");
        Path tempDir = createTempDir("deno-extract");
        if (tempDir == null) {
            return new ResolvedBinary(BinaryType.DENO, null, null, null, false, "Failed to create temp dir");
        }
        ResolvedBinary download = downloadArchive(BinaryType.DENO, url, archive, MAX_DOWNLOAD_DENO);
        if (!download.isValid()) {
            return download;
        }
        try {
            extractZip(archive, tempDir, new BundleSpec(new BundleFile("deno", "deno")));
            Path binary = tempDir.resolve("deno");
            if (!Files.exists(binary)) {
                return new ResolvedBinary(BinaryType.DENO, null, null, null, false, "Downloaded deno archive missing binary");
            }
            Path target = environmentInfo.getInstallDir().resolve("deno");
            withInstallLock(() -> {
                moveAtomically(binary, target);
                makeExecutable(target);
            });
            ResolvedBinary resolved = validateCandidate(BinaryType.DENO, new Candidate(BinarySource.CACHE, target));
            markUpdateCheck(BinaryType.DENO);
            resolvedCache.put(BinaryType.DENO, resolved);
            logResolved(resolved);
            return resolved;
        } catch (IOException e) {
            return new ResolvedBinary(BinaryType.DENO, null, null, null, false, "Download failed: " + e.getMessage());
        } finally {
            cleanTemp(tempDir);
            deleteIfExists(archive);
        }
    }

    private void downloadFfmpegBundle(boolean forceDownload, boolean ignoreSystem, boolean preserveOnFailure) {
        if (!supportsDownloads()) {
            ResolvedBinary failed = new ResolvedBinary(BinaryType.FFMPEG, null, null, null, false, "Automatic downloads are only supported on Linux.");
            if (!preserveOnFailure) {
                resolvedCache.put(BinaryType.FFMPEG, failed);
                resolvedCache.put(BinaryType.FFPROBE, failed);
            }
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> downloadFfmpegBundle(forceDownload, ignoreSystem));
            return;
        }
        if (!forceDownload && !ignoreSystem) {
            Candidate systemFfmpeg = findSystemCandidate(BinaryType.FFMPEG);
            Candidate systemProbe = findSystemCandidate(BinaryType.FFPROBE);
            if (systemFfmpeg != null && systemProbe != null) {
                ResolvedBinary resolvedFfmpeg = validateCandidate(BinaryType.FFMPEG, systemFfmpeg);
                ResolvedBinary resolvedProbe = validateCandidate(BinaryType.FFPROBE, systemProbe);
                if (resolvedFfmpeg != null && resolvedProbe != null && resolvedFfmpeg.isValid() && resolvedProbe.isValid()) {
                    resolvedCache.put(BinaryType.FFMPEG, resolvedFfmpeg);
                    resolvedCache.put(BinaryType.FFPROBE, resolvedProbe);
                    logResolved(resolvedFfmpeg);
                    logResolved(resolvedProbe);
                    return;
                }
            }
        }

        FfmpegBundle bundle = resolveFfmpegBundle(detectArch());
        if (bundle == null) {
            ResolvedBinary failed = new ResolvedBinary(BinaryType.FFMPEG, null, null, null, false, "Unsupported OS/arch for ffmpeg bundle");
            resolvedCache.put(BinaryType.FFMPEG, failed);
            resolvedCache.put(BinaryType.FFPROBE, failed);
            return;
        }

        Path archivePath = environmentInfo.getInstallDir().resolve("ffmpeg-" + bundle.archiveSuffix);
        Path tempDir = createTempDir("ffmpeg-extract");
        if (tempDir == null) {
            ResolvedBinary failed = new ResolvedBinary(BinaryType.FFMPEG, null, null, null, false, "Failed to create temp dir");
            if (!preserveOnFailure) {
                resolvedCache.put(BinaryType.FFMPEG, failed);
                resolvedCache.put(BinaryType.FFPROBE, failed);
            }
            return;
        }

        ResolvedBinary download = downloadArchive(BinaryType.FFMPEG, bundle.url, archivePath, MAX_DOWNLOAD_FFMPEG);
        if (!download.isValid()) {
            if (!preserveOnFailure) {
                resolvedCache.put(BinaryType.FFMPEG, download);
                resolvedCache.put(BinaryType.FFPROBE, download);
            }
            return;
        }

        try {
            if (bundle.format == ArchiveFormat.TAR_XZ) {
                extractTarXz(archivePath, tempDir, bundle.bundle);
            } else {
                extractZip(archivePath, tempDir, bundle.bundle);
            }
            Path ffmpegTemp = tempDir.resolve("ffmpeg");
            Path ffprobeTemp = tempDir.resolve("ffprobe");
            if (!Files.exists(ffmpegTemp) || !Files.exists(ffprobeTemp)) {
                ResolvedBinary failed = new ResolvedBinary(BinaryType.FFMPEG, null, null, null, false, "Downloaded ffmpeg bundle missing binaries");
                if (!preserveOnFailure) {
                    resolvedCache.put(BinaryType.FFMPEG, failed);
                    resolvedCache.put(BinaryType.FFPROBE, failed);
                }
                return;
            }
            Path ffmpegTarget = environmentInfo.getInstallDir().resolve("ffmpeg");
            Path ffprobeTarget = environmentInfo.getInstallDir().resolve("ffprobe");
            withInstallLock(() -> {
                moveAtomically(ffmpegTemp, ffmpegTarget);
                moveAtomically(ffprobeTemp, ffprobeTarget);
                makeExecutable(ffmpegTarget);
                makeExecutable(ffprobeTarget);
            });

            ResolvedBinary resolvedFfmpeg = validateCandidate(BinaryType.FFMPEG, new Candidate(BinarySource.CACHE, ffmpegTarget));
            ResolvedBinary resolvedProbe = validateCandidate(BinaryType.FFPROBE, new Candidate(BinarySource.CACHE, ffprobeTarget));
            markUpdateCheck(BinaryType.FFMPEG);
            markUpdateCheck(BinaryType.FFPROBE);
            resolvedCache.put(BinaryType.FFMPEG, resolvedFfmpeg);
            resolvedCache.put(BinaryType.FFPROBE, resolvedProbe);
            logResolved(resolvedFfmpeg);
            logResolved(resolvedProbe);
        } catch (IOException e) {
            ResolvedBinary failed = new ResolvedBinary(BinaryType.FFMPEG, null, null, null, false, "Download failed: " + e.getMessage());
            if (!preserveOnFailure) {
                resolvedCache.put(BinaryType.FFMPEG, failed);
                resolvedCache.put(BinaryType.FFPROBE, failed);
            }
        } finally {
            cleanTemp(tempDir);
            deleteIfExists(archivePath);
        }
    }

    private ResolvedBinary downloadBinaryFile(BinaryType type, String url, Path target, long maxSize) {
        try {
            withInstallLock(() -> {
                ensureDirectory(environmentInfo.getInstallDir());
                Path temp = createTempFile(environmentInfo.getInstallDir(), type.commandName());
                try {
                    downloadToFile(url, temp, maxSize);
                    if (Files.size(temp) < minimumSize(type)) {
                        throw new IOException("Downloaded file too small");
                    }
                    if (!validateArch(temp)) {
                        throw new IOException("Downloaded file has wrong architecture");
                    }
                    moveAtomically(temp, target);
                    makeExecutable(target);
                } finally {
                    deleteIfExists(temp);
                }
            });
            ResolvedBinary resolved = validateCandidate(type, new Candidate(BinarySource.CACHE, target));
            markUpdateCheck(type);
            resolvedCache.put(type, resolved);
            logResolved(resolved);
            return resolved;
        } catch (IOException e) {
            logDownloadFailure(type, url, e);
            return new ResolvedBinary(type, null, null, null, false, "Download failed: " + e.getMessage());
        }
    }

    private ResolvedBinary downloadArchive(BinaryType type, String url, Path archivePath, long maxSize) {
        try {
            withInstallLock(() -> {
                ensureDirectory(environmentInfo.getInstallDir());
                Path temp = createTempFile(environmentInfo.getInstallDir(), "download");
                try {
                    downloadToFile(url, temp, maxSize);
                    moveAtomically(temp, archivePath);
                } finally {
                    deleteIfExists(temp);
                }
            });
            return new ResolvedBinary(type, archivePath, BinarySource.CACHE, "downloaded", true, null);
        } catch (IOException e) {
            logDownloadFailure(type, url, e);
            return new ResolvedBinary(type, null, null, null, false, "Download failed: " + e.getMessage());
        }
    }

    private void withInstallLock(IoRunnable action) throws IOException {
        ensureDirectory(environmentInfo.getInstallDir());
        Path lockPath = environmentInfo.getInstallDir().resolve(".mediaplayer.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            action.run();
        }
    }

    private Path createTempFile(Path directory, String prefix) throws IOException {
        return Files.createTempFile(directory, prefix, ".tmp");
    }

    private Path createTempDir(String prefix) {
        try {
            return Files.createTempDirectory(environmentInfo.getInstallDir(), prefix);
        } catch (IOException e) {
            return null;
        }
    }

    private void downloadToFile(String url, Path target, long maxSize) throws IOException {
        URL source = URI.create(url).toURL();
        if (!"https".equalsIgnoreCase(source.getProtocol())) {
            throw new IOException("Non-HTTPS download blocked");
        }
        HttpURLConnection connection = (HttpURLConnection) source.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout((int) DEFAULT_DOWNLOAD_TIMEOUT_MS);
        connection.setReadTimeout((int) DEFAULT_DOWNLOAD_TIMEOUT_MS);
        int code = connection.getResponseCode();
        if (code >= 300 && code < 400) {
            String location = connection.getHeaderField("Location");
            if (location != null) {
                URL redirected = URI.create(location).toURL();
                if (!"https".equalsIgnoreCase(redirected.getProtocol())) {
                    throw new IOException("Non-HTTPS redirect blocked");
                }
                connection = (HttpURLConnection) redirected.openConnection();
                connection.setConnectTimeout((int) DEFAULT_DOWNLOAD_TIMEOUT_MS);
                connection.setReadTimeout((int) DEFAULT_DOWNLOAD_TIMEOUT_MS);
                code = connection.getResponseCode();
            }
        }
        if (code >= 400) {
            throw new IOException("HTTP " + code);
        }
        long length = connection.getContentLengthLong();
        if (length > 0 && length > maxSize) {
            throw new IOException("Download exceeds size limit");
        }
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[8192];
            long readTotal = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                readTotal += read;
                if (readTotal > maxSize) {
                    throw new IOException("Download exceeds size limit");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private void cleanTemp(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    stream.forEach(this::deleteIfExists);
                }
                Files.deleteIfExists(path);
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
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

    private FfmpegBundle resolveFfmpegBundle(String arch) {
        String suffix = "aarch64".equals(arch) ? "linuxarm64" : "linux64";
        return new FfmpegBundle("https://github.com/yt-dlp/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-" + suffix + "-gpl.tar.xz",
                ArchiveFormat.TAR_XZ, suffix + ".tar.xz",
                new BundleSpec(new BundleFile("ffmpeg", "ffmpeg"), new BundleFile("ffprobe", "ffprobe")));
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

    private void logDownloadFailure(BinaryType type, String url, IOException error) {
        Bukkit.getLogger().warning("[MediaPlayer]: Failed to download/extract " + type.commandName()
                + " from " + url + " (" + error.getMessage() + "). Feature will be disabled.");
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static class Candidate {
        private final BinarySource source;
        private final Path path;

        Candidate(BinarySource source, Path path) {
            this.source = source;
            this.path = path;
        }
    }

    @FunctionalInterface
    private interface IoRunnable {
        void run() throws IOException;
    }
}
