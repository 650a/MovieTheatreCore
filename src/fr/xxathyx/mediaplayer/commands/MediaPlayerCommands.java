package fr.xxathyx.mediaplayer.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.configuration.Configuration;
import fr.xxathyx.mediaplayer.dependency.DependencyManager;
import fr.xxathyx.mediaplayer.playback.PlaybackManager;
import fr.xxathyx.mediaplayer.render.ScalingMode;
import fr.xxathyx.mediaplayer.screen.Screen;
import fr.xxathyx.mediaplayer.screen.ScreenManager;
import fr.xxathyx.mediaplayer.video.Video;

public class MediaPlayerCommands implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final Configuration configuration;

    public MediaPlayerCommands(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("mediaplayer")) {
            return false;
        }

        if (!sender.hasPermission("mediaplayer.command")) {
            sender.sendMessage(configuration.insufficient_permissions());
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        ScreenManager screenManager = plugin.getScreenManager();
        PlaybackManager playbackManager = plugin.getPlaybackManager();

        List<String> filteredArgs = new ArrayList<>(Arrays.asList(args));
        boolean noAudio = filteredArgs.removeIf(arg -> arg.equalsIgnoreCase("--noaudio"));

        if (filteredArgs.isEmpty()) {
            sendHelp(sender);
            return true;
        }

        switch (filteredArgs.get(0).toLowerCase()) {
            case "screen" -> {
                if (filteredArgs.size() < 2) {
                    sendScreenHelp(sender);
                    return true;
                }

                switch (filteredArgs.get(1).toLowerCase()) {
                    case "create" -> {
                        if (!sender.hasPermission("mediaplayer.screen.manage")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(ChatColor.RED + "Only players can create screens.");
                            return true;
                        }
                        if (filteredArgs.size() < 5) {
                            sender.sendMessage(ChatColor.RED + "/mp screen create <name> <width> <height>");
                            return true;
                        }
                        String name = filteredArgs.get(2);
                        int width = parseInt(sender, filteredArgs.get(3));
                        int height = parseInt(sender, filteredArgs.get(4));
                        if (width <= 0 || height <= 0) {
                            return true;
                        }
                        Screen screen = screenManager.createScreen(player, name, width, height);
                        if (screen == null) {
                            sender.sendMessage(configuration.screen_cannot_create());
                            return true;
                        }
                        sender.sendMessage(ChatColor.GREEN + "Screen created: " + screen.getName() + " (" + screen.getWidth() + "x" + screen.getHeight() + ").");
                        return true;
                    }
                    case "delete" -> {
                        if (!sender.hasPermission("mediaplayer.screen.manage")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        if (filteredArgs.size() < 3) {
                            sender.sendMessage(ChatColor.RED + "/mp screen delete <name>");
                            return true;
                        }
                        Screen screen = resolveScreen(screenManager, filteredArgs.get(2));
                        if (screen == null) {
                            sender.sendMessage(ChatColor.RED + "Unknown screen: " + filteredArgs.get(2));
                            return true;
                        }
                        playbackManager.stop(screen, null, true);
                        screenManager.deleteScreen(screen);
                        sender.sendMessage(ChatColor.GREEN + "Screen deleted: " + screen.getName());
                        return true;
                    }
                    case "list" -> {
                        if (sender instanceof Player player) {
                            plugin.getGuiSupport().openScreenManager(player);
                        } else {
                            sender.sendMessage(ChatColor.GOLD + "Screens: ");
                            for (Screen screen : screenManager.getScreens().values()) {
                                sender.sendMessage(ChatColor.GRAY + "- " + screen.getName() + " (" + screen.getWidth() + "x" + screen.getHeight() + ")");
                            }
                        }
                        return true;
                    }
                    default -> {
                        sendScreenHelp(sender);
                        return true;
                    }
                }
            }
            case "media" -> {
                if (filteredArgs.size() < 2) {
                    sendMediaHelp(sender);
                    return true;
                }
                switch (filteredArgs.get(1).toLowerCase()) {
                    case "add" -> {
                        if (!sender.hasPermission("mediaplayer.media.admin")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        if (filteredArgs.size() < 4) {
                            sender.sendMessage(ChatColor.RED + "/mp media add <name> <url>");
                            return true;
                        }
                        plugin.getMediaManager().addMedia(sender, filteredArgs.get(2), filteredArgs.get(3));
                        return true;
                    }
                    case "remove" -> {
                        if (!sender.hasPermission("mediaplayer.media.admin")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        if (filteredArgs.size() < 3) {
                            sender.sendMessage(ChatColor.RED + "/mp media remove <name>");
                            return true;
                        }
                        plugin.getMediaManager().removeMedia(sender, filteredArgs.get(2));
                        return true;
                    }
                    case "list" -> {
                        if (!sender.hasPermission("mediaplayer.media.manage")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        plugin.getMediaManager().listMedia(sender);
                        return true;
                    }
                    default -> {
                        sendMediaHelp(sender);
                        return true;
                    }
                }
            }
            case "play" -> {
                if (!sender.hasPermission("mediaplayer.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 3) {
                    sender.sendMessage(ChatColor.RED + "/mp play <screen> <source> [--noaudio]");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, filteredArgs.get(1));
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + filteredArgs.get(1));
                    return true;
                }
                if (filteredArgs.size() >= 4 && filteredArgs.get(2).equalsIgnoreCase("media")) {
                    if (!sender.hasPermission("mediaplayer.media.manage")) {
                        sender.sendMessage(configuration.insufficient_permissions());
                        return true;
                    }
                    plugin.getMediaManager().playMedia(sender, screen, filteredArgs.get(3), noAudio);
                    return true;
                }
                if (filteredArgs.size() >= 4 && filteredArgs.get(2).equalsIgnoreCase("url")) {
                    if (!sender.hasPermission("mediaplayer.media.admin")) {
                        sender.sendMessage(configuration.insufficient_permissions());
                        return true;
                    }
                    plugin.getMediaManager().playUrl(sender, screen, filteredArgs.get(3), noAudio);
                    return true;
                }
                Video video = resolveVideo(filteredArgs.get(2));
                if (video == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown video: " + filteredArgs.get(2));
                    return true;
                }
                playbackManager.start(screen, video, new fr.xxathyx.mediaplayer.playback.PlaybackOptions(!noAudio, null, null));
                sender.sendMessage(ChatColor.GREEN + "Playing " + video.getName() + " on screen " + screen.getName() + ".");
                return true;
            }
            case "diagnose" -> {
                sendDiagnostics(sender);
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("mediaplayer.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 2) {
                    sender.sendMessage(ChatColor.RED + "/mp stop <screen>");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, filteredArgs.get(1));
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + filteredArgs.get(1));
                    return true;
                }
                playbackManager.stop(screen, null, true);
                sender.sendMessage(ChatColor.GREEN + "Stopped playback on " + screen.getName() + ".");
                return true;
            }
            case "pause" -> {
                if (!sender.hasPermission("mediaplayer.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 2) {
                    sender.sendMessage(ChatColor.RED + "/mp pause <screen>");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, filteredArgs.get(1));
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + filteredArgs.get(1));
                    return true;
                }
                playbackManager.pause(screen);
                sender.sendMessage(ChatColor.GREEN + "Paused playback on " + screen.getName() + ".");
                return true;
            }
            case "resume" -> {
                if (!sender.hasPermission("mediaplayer.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 2) {
                    sender.sendMessage(ChatColor.RED + "/mp resume <screen>");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, filteredArgs.get(1));
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + filteredArgs.get(1));
                    return true;
                }
                playbackManager.resume(screen);
                sender.sendMessage(ChatColor.GREEN + "Resumed playback on " + screen.getName() + ".");
                return true;
            }
            case "scale" -> {
                if (!sender.hasPermission("mediaplayer.screen.manage")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 3) {
                    sender.sendMessage(ChatColor.RED + "/mp scale <screen> <fit|fill|stretch>");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, filteredArgs.get(1));
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + filteredArgs.get(1));
                    return true;
                }
                ScalingMode mode = parseScalingMode(filteredArgs.get(2));
                if (mode == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown scale mode: " + filteredArgs.get(2));
                    return true;
                }
                screen.setScaleMode(mode);
                sender.sendMessage(ChatColor.GREEN + "Scale mode for " + screen.getName() + " set to " + mode.name() + ".");
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("mediaplayer.admin")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                playbackManager.stopAll();
                screenManager.loadAll();
                sender.sendMessage(ChatColor.GREEN + "MediaPlayer reloaded.");
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if(!Bukkit.isPrimaryThread()) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        try {
            if (args.length == 1) {
                List<String> candidates = List.of("screen", "media", "play", "stop", "pause", "resume", "scale", "reload", "diagnose");
                StringUtil.copyPartialMatches(args[0], candidates, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("screen")) {
                List<String> candidates = List.of("create", "delete", "list");
                StringUtil.copyPartialMatches(args[1], candidates, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("media")) {
                List<String> candidates = List.of("add", "remove", "list");
                StringUtil.copyPartialMatches(args[1], candidates, completions);
            } else if (args.length == 2 && List.of("play", "stop", "pause", "resume", "scale").contains(args[0].toLowerCase())) {
                List<String> candidates = new ArrayList<>();
                for (Screen screen : plugin.getScreenManager().getScreens().values()) {
                    candidates.add(screen.getName());
                }
                StringUtil.copyPartialMatches(args[1], candidates, completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
                List<String> candidates = List.of("media", "url");
                StringUtil.copyPartialMatches(args[2], candidates, completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("scale")) {
                List<String> candidates = List.of("fit", "fill", "stretch");
                StringUtil.copyPartialMatches(args[2], candidates, completions);
            } else if (args.length == 4 && args[0].equalsIgnoreCase("play") && args[2].equalsIgnoreCase("media")) {
                List<String> candidates = new ArrayList<>();
                for (fr.xxathyx.mediaplayer.media.MediaEntry entry : plugin.getMediaLibrary().listEntries()) {
                    candidates.add(entry.getName());
                }
                StringUtil.copyPartialMatches(args[3], candidates, completions);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Tab completion failed: " + e.getMessage());
            return Collections.emptyList();
        }

        Collections.sort(completions);
        return completions;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "MediaPlayer commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen create <name> <w> <h>");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen delete <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen list");
        sender.sendMessage(ChatColor.YELLOW + "/mp media add <name> <url>");
        sender.sendMessage(ChatColor.YELLOW + "/mp media remove <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mp media list");
        sender.sendMessage(ChatColor.YELLOW + "/mp play <screen> <source>");
        sender.sendMessage(ChatColor.YELLOW + "/mp play <screen> media <name> [--noaudio]");
        sender.sendMessage(ChatColor.YELLOW + "/mp play <screen> url <url> [--noaudio]");
        sender.sendMessage(ChatColor.YELLOW + "/mp stop <screen>");
        sender.sendMessage(ChatColor.YELLOW + "/mp pause <screen>");
        sender.sendMessage(ChatColor.YELLOW + "/mp resume <screen>");
        sender.sendMessage(ChatColor.YELLOW + "/mp scale <screen> <fit|fill|stretch>");
        sender.sendMessage(ChatColor.YELLOW + "/mp reload");
        sender.sendMessage(ChatColor.YELLOW + "/mp diagnose");
    }

    private void sendScreenHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Screen commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen create <name> <w> <h>");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen delete <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen list");
    }

    private void sendMediaHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Media commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mp media add <name> <url>");
        sender.sendMessage(ChatColor.YELLOW + "/mp media remove <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mp media list");
    }

    private void sendDiagnostics(CommandSender sender) {
        DependencyManager dependencyManager = plugin.getDependencyManager();
        DependencyManager.EnvironmentInfo env = dependencyManager.getEnvironmentInfo();
        sender.sendMessage(ChatColor.GOLD + "MediaPlayer diagnose:");
        sender.sendMessage(ChatColor.GRAY + "OS/arch: " + env.getOs() + "/" + env.getArch());
        sender.sendMessage(ChatColor.GRAY + "Plugin dir executable: " + yesNo(env.isPluginDirExecutable()));
        sender.sendMessage(ChatColor.GRAY + "Rootfs read-only: " + yesNo(env.isRootReadOnly()));
        sender.sendMessage(ChatColor.GRAY + "Staging dir: " + env.getStagingDir());
        sender.sendMessage(ChatColor.GRAY + "Staging dir writable: " + yesNo(env.isStagingWritable()));
        sender.sendMessage(ChatColor.GRAY + "Staging dir executable: " + yesNo(env.isStagingExecutable()));

        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.FFMPEG, false), "ffmpeg");
        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.FFPROBE, false), "ffprobe");
        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.YT_DLP, false), "yt-dlp");
        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.DENO, false), "deno");

        String cookiesPath = configuration.media_youtube_cookies_path();
        if (cookiesPath == null || cookiesPath.isBlank()) {
            sender.sendMessage(ChatColor.GRAY + "Cookies file: not configured");
        } else {
            File cookies = new File(cookiesPath);
            sender.sendMessage(ChatColor.GRAY + "Cookies file: " + cookies.getPath() + " (readable=" + yesNo(cookies.exists() && cookies.canRead()) + ")");
        }

        Integer exitCode = plugin.getMediaManager().getLastResolverExitCode();
        String stderr = plugin.getMediaManager().getLastResolverError();
        sender.sendMessage(ChatColor.GRAY + "Last resolver exit code: " + (exitCode == null ? "n/a" : exitCode));
        sender.sendMessage(ChatColor.GRAY + "Last resolver stderr: " + (stderr == null || stderr.isBlank() ? "n/a" : stderr));
    }

    private void reportBinary(CommandSender sender, DependencyManager.ResolvedBinary resolved, String label) {
        if (resolved == null || !resolved.isValid() || resolved.getStagedPath() == null) {
            sender.sendMessage(ChatColor.RED + label + ": missing");
            if (resolved != null && resolved.getError() != null) {
                sender.sendMessage(ChatColor.DARK_RED + "  " + resolved.getError());
            }
            return;
        }
        sender.sendMessage(ChatColor.GRAY + label + ": " + resolved.getStagedPath() + " (" + resolved.getVersion() + ")");
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private int parseInt(CommandSender sender, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number: " + value);
            return -1;
        }
    }

    private Screen resolveScreen(ScreenManager manager, String value) {
        Screen byName = manager.getScreenByName(value);
        if (byName != null) {
            return byName;
        }
        try {
            UUID uuid = UUID.fromString(value);
            return manager.getScreen(uuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Video resolveVideo(String name) {
        for (Video video : plugin.getRegisteredVideos()) {
            if (video.getName().equalsIgnoreCase(name)) {
                return video;
            }
        }
        return null;
    }

    private ScalingMode parseScalingMode(String value) {
        try {
            return ScalingMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
