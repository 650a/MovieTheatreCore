package fr.xxathyx.mediaplayer.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.configuration.Configuration;
import fr.xxathyx.mediaplayer.gui.ScreenManagerMenu;
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

        switch (args[0].toLowerCase()) {
            case "screen" -> {
                if (args.length < 2) {
                    sendScreenHelp(sender);
                    return true;
                }

                switch (args[1].toLowerCase()) {
                    case "create" -> {
                        if (!sender.hasPermission("mediaplayer.screen.manage")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(ChatColor.RED + "Only players can create screens.");
                            return true;
                        }
                        if (args.length < 5) {
                            sender.sendMessage(ChatColor.RED + "/mp screen create <name> <width> <height>");
                            return true;
                        }
                        String name = args[2];
                        int width = parseInt(sender, args[3]);
                        int height = parseInt(sender, args[4]);
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
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "/mp screen delete <name>");
                            return true;
                        }
                        Screen screen = resolveScreen(screenManager, args[2]);
                        if (screen == null) {
                            sender.sendMessage(ChatColor.RED + "Unknown screen: " + args[2]);
                            return true;
                        }
                        playbackManager.stop(screen, null);
                        screenManager.deleteScreen(screen);
                        sender.sendMessage(ChatColor.GREEN + "Screen deleted: " + screen.getName());
                        return true;
                    }
                    case "list" -> {
                        if (sender instanceof Player player) {
                            new ScreenManagerMenu(plugin).open(player);
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
            case "play" -> {
                if (!sender.hasPermission("mediaplayer.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "/mp play <screen> <source>");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, args[1]);
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + args[1]);
                    return true;
                }
                Video video = resolveVideo(args[2]);
                if (video == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown video: " + args[2]);
                    return true;
                }
                playbackManager.start(screen, video);
                sender.sendMessage(ChatColor.GREEN + "Playing " + video.getName() + " on screen " + screen.getName() + ".");
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("mediaplayer.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "/mp stop <screen>");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, args[1]);
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + args[1]);
                    return true;
                }
                playbackManager.stop(screen, null);
                sender.sendMessage(ChatColor.GREEN + "Stopped playback on " + screen.getName() + ".");
                return true;
            }
            case "pause" -> {
                if (!sender.hasPermission("mediaplayer.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "/mp pause <screen>");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, args[1]);
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + args[1]);
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
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "/mp resume <screen>");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, args[1]);
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + args[1]);
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
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "/mp scale <screen> <fit|fill|stretch>");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, args[1]);
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + args[1]);
                    return true;
                }
                ScalingMode mode = parseScalingMode(args[2]);
                if (mode == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown scale mode: " + args[2]);
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
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("screen", "play", "stop", "pause", "resume", "scale", "reload"), completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("screen")) {
            StringUtil.copyPartialMatches(args[1], Arrays.asList("create", "delete", "list"), completions);
        } else if (args.length == 2 && Arrays.asList("play", "stop", "pause", "resume", "scale").contains(args[0].toLowerCase())) {
            for (Screen screen : plugin.getScreenManager().getScreens().values()) {
                completions.add(screen.getName());
            }
            StringUtil.copyPartialMatches(args[1], completions, completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("scale")) {
            StringUtil.copyPartialMatches(args[2], Arrays.asList("fit", "fill", "stretch"), completions);
        }

        Collections.sort(completions);
        return completions;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "MediaPlayer commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen create <name> <w> <h>");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen delete <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen list");
        sender.sendMessage(ChatColor.YELLOW + "/mp play <screen> <source>");
        sender.sendMessage(ChatColor.YELLOW + "/mp stop <screen>");
        sender.sendMessage(ChatColor.YELLOW + "/mp pause <screen>");
        sender.sendMessage(ChatColor.YELLOW + "/mp resume <screen>");
        sender.sendMessage(ChatColor.YELLOW + "/mp scale <screen> <fit|fill|stretch>");
        sender.sendMessage(ChatColor.YELLOW + "/mp reload");
    }

    private void sendScreenHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Screen commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen create <name> <w> <h>");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen delete <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mp screen list");
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
