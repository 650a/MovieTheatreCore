package com._650a.movietheatrecore.commands;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.dependency.DependencyManager;
import com._650a.movietheatrecore.playback.PlaybackManager;
import com._650a.movietheatrecore.resourcepack.EmbeddedPackServer;
import com._650a.movietheatrecore.render.ScalingMode;
import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.screen.ScreenManager;
import com._650a.movietheatrecore.theatre.ShowRepeat;
import com._650a.movietheatrecore.theatre.ShowScheduleEntry;
import com._650a.movietheatrecore.theatre.TheatreManager;
import com._650a.movietheatrecore.theatre.TheatreRoom;
import com._650a.movietheatrecore.util.PermissionUtil;
import com._650a.movietheatrecore.update.Updater;
import com._650a.movietheatrecore.video.Video;

public class MovieTheatreCoreCommands implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final Configuration configuration;

    public MovieTheatreCoreCommands(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("movietheatrecore")) {
            return false;
        }

        if (!PermissionUtil.hasPermission(sender, "movietheatrecore.command")) {
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
                        if (!PermissionUtil.hasPermission(sender, "movietheatrecore.screen.manage")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(ChatColor.RED + "Only players can create screens.");
                            return true;
                        }
                        if (filteredArgs.size() < 5) {
                            sender.sendMessage(ChatColor.RED + "/mtc screen create <name> <width> <height>");
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
                        if (!PermissionUtil.hasPermission(sender, "movietheatrecore.screen.manage")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        if (filteredArgs.size() < 3) {
                            sender.sendMessage(ChatColor.RED + "/mtc screen delete <name>");
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
                        if (!PermissionUtil.hasPermission(sender, "movietheatrecore.media.admin")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        List<String> addArgs = new ArrayList<>(filteredArgs.subList(2, filteredArgs.size()));
                        String customId = null;
                        for (int i = 0; i < addArgs.size(); i++) {
                            String token = addArgs.get(i);
                            if (token.equalsIgnoreCase("--id")) {
                                if (i + 1 >= addArgs.size()) {
                                    sender.sendMessage(ChatColor.RED + "Missing value for --id.");
                                    sendMediaAddHelp(sender);
                                    return true;
                                }
                                customId = addArgs.get(i + 1);
                                addArgs.remove(i + 1);
                                addArgs.remove(i);
                                i--;
                                continue;
                            }
                            if (token.toLowerCase(Locale.ROOT).startsWith("--id=")) {
                                customId = token.substring("--id=".length());
                                if (customId.isBlank()) {
                                    sender.sendMessage(ChatColor.RED + "Media ID cannot be empty.");
                                    sendMediaAddHelp(sender);
                                    return true;
                                }
                                addArgs.remove(i);
                                i--;
                            }
                        }
                        if (addArgs.size() < 2 || addArgs.size() > 3) {
                            sendMediaAddHelp(sender);
                            return true;
                        }
                        String name = addArgs.get(0);
                        String url = resolveMediaAddUrl(sender, addArgs);
                        if (url == null) {
                            sendMediaAddHelp(sender);
                            return true;
                        }
                        if (customId != null && customId.isBlank()) {
                            sender.sendMessage(ChatColor.RED + "Media ID cannot be empty.");
                            sendMediaAddHelp(sender);
                            return true;
                        }
                        plugin.getMediaManager().addMedia(sender, name, url, customId);
                        return true;
                    }
                    case "remove" -> {
                        if (!PermissionUtil.hasPermission(sender, "movietheatrecore.media.admin")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        if (filteredArgs.size() < 3) {
                            sender.sendMessage(ChatColor.RED + "/mtc media remove <name>");
                            return true;
                        }
                        plugin.getMediaManager().removeMedia(sender, filteredArgs.get(2));
                        return true;
                    }
                    case "list" -> {
                        if (!PermissionUtil.hasPermission(sender, "movietheatrecore.media.manage")) {
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
                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 3) {
                    sender.sendMessage(ChatColor.RED + "/mtc play <screen> <source> [--noaudio]");
                    return true;
                }
                Screen screen = resolveScreen(screenManager, filteredArgs.get(1));
                if (screen == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown screen: " + filteredArgs.get(1));
                    return true;
                }
                if (filteredArgs.size() >= 4 && filteredArgs.get(2).equalsIgnoreCase("media")) {
                    if (!PermissionUtil.hasPermission(sender, "movietheatrecore.media.manage")) {
                        sender.sendMessage(configuration.insufficient_permissions());
                        return true;
                    }
                    plugin.getMediaManager().playMedia(sender, screen, filteredArgs.get(3), noAudio);
                    return true;
                }
                if (filteredArgs.size() >= 4 && filteredArgs.get(2).equalsIgnoreCase("url")) {
                    if (!PermissionUtil.hasPermission(sender, "movietheatrecore.media.admin")) {
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
                playbackManager.start(screen, video, new com._650a.movietheatrecore.playback.PlaybackOptions(!noAudio, null, null));
                sender.sendMessage(ChatColor.GREEN + "Playing " + video.getName() + " on screen " + screen.getName() + ".");
                return true;
            }
            case "diagnose" -> {
                sendDiagnostics(sender);
                return true;
            }
            case "update" -> {
                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.admin")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 2 || !filteredArgs.get(1).equalsIgnoreCase("check")) {
                    sender.sendMessage(ChatColor.RED + "/mtc update check");
                    return true;
                }
                Updater.UpdateCheckResult result = plugin.getUpdater().checkForUpdates(true);
                sender.sendMessage(ChatColor.GOLD + "MovieTheatreCore update check:");
                sender.sendMessage(ChatColor.GRAY + "URL: " + result.getUrl());
                sender.sendMessage(ChatColor.GRAY + "Status: " + result.getStatus());
                sender.sendMessage(ChatColor.GRAY + "Info: " + result.getMessage());
                return true;
            }
            case "deps" -> {
                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.deps.manage")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 2) {
                    sendDepsHelp(sender);
                    return true;
                }
                switch (filteredArgs.get(1).toLowerCase()) {
                    case "status" -> {
                        sendDepsStatus(sender);
                        return true;
                    }
                    case "reinstall" -> {
                        sender.sendMessage(ChatColor.YELLOW + "Reinstalling MovieTheatreCore dependencies in the background...");
                        plugin.getDependencyManager().reinstallAllAsync(() ->
                                sender.sendMessage(ChatColor.GREEN + "Dependency reinstall complete. Run /mtc deps status to verify."));
                        return true;
                    }
                    default -> {
                        sendDepsHelp(sender);
                        return true;
                    }
                }
            }
            case "theatre" -> {
                TheatreManager theatreManager = plugin.getTheatreManager();
                if (theatreManager == null) {
                    sender.sendMessage(ChatColor.RED + "Theatre system is not available.");
                    return true;
                }
                if (filteredArgs.size() < 2) {
                    sendTheatreHelp(sender);
                    return true;
                }
                switch (filteredArgs.get(1).toLowerCase()) {
                    case "room" -> {
                        if (filteredArgs.size() < 3) {
                            sendTheatreRoomHelp(sender);
                            return true;
                        }
                        switch (filteredArgs.get(2).toLowerCase()) {
                            case "create" -> {
                                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.theatre.room.create")) {
                                    sender.sendMessage(configuration.insufficient_permissions());
                                    return true;
                                }
                                if (filteredArgs.size() < 4) {
                                    sender.sendMessage(ChatColor.RED + "/mtc theatre room create <name> [screen...]");
                                    return true;
                                }
                                String name = filteredArgs.get(3);
                                List<String> screens = new ArrayList<>();
                                for (int i = 4; i < filteredArgs.size(); i++) {
                                    String screenName = filteredArgs.get(i);
                                    Screen screen = screenManager.getScreenByName(screenName);
                                    if (screen == null) {
                                        sender.sendMessage(ChatColor.YELLOW + "Unknown screen ignored: " + screenName);
                                    } else {
                                        screens.add(screen.getName());
                                    }
                                }
                                theatreManager.createRoom(sender, name, screens);
                                return true;
                            }
                            case "delete" -> {
                                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.theatre.room.delete")) {
                                    sender.sendMessage(configuration.insufficient_permissions());
                                    return true;
                                }
                                if (filteredArgs.size() < 4) {
                                    sender.sendMessage(ChatColor.RED + "/mtc theatre room delete <name>");
                                    return true;
                                }
                                theatreManager.deleteRoom(sender, filteredArgs.get(3));
                                return true;
                            }
                            default -> {
                                sendTheatreRoomHelp(sender);
                                return true;
                            }
                        }
                    }
                    case "schedule" -> {
                        if (filteredArgs.size() < 3) {
                            sendTheatreScheduleHelp(sender);
                            return true;
                        }
                        String action = filteredArgs.get(2).toLowerCase();
                        if (action.equals("add")) {
                            if (!PermissionUtil.hasPermission(sender, "movietheatrecore.theatre.schedule.add")) {
                                sender.sendMessage(configuration.insufficient_permissions());
                                return true;
                            }
                            if (filteredArgs.size() < 6) {
                                sender.sendMessage(ChatColor.RED + "/mtc theatre schedule add <room> <HH:MM> <mediaId> [repeat=daily|weekly|none]");
                                return true;
                            }
                            TheatreRoom room = theatreManager.getRoom(filteredArgs.get(3));
                            if (room == null) {
                                sender.sendMessage(ChatColor.RED + "Unknown room: " + filteredArgs.get(3));
                                return true;
                            }
                            java.time.LocalTime time;
                            try {
                                time = java.time.LocalTime.parse(filteredArgs.get(4), java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                            } catch (Exception e) {
                                sender.sendMessage(ChatColor.RED + "Invalid time format. Use HH:MM.");
                                return true;
                            }
                            String repeatValue = "none";
                            if (filteredArgs.size() >= 7) {
                                String raw = filteredArgs.get(6).toLowerCase(Locale.ROOT);
                                repeatValue = raw.startsWith("repeat=") ? raw.substring("repeat=".length()) : raw;
                            }
                            ShowRepeat repeat = ShowRepeat.fromString(repeatValue);
                            ShowScheduleEntry entry = theatreManager.addSchedule(room, time, repeat, filteredArgs.get(5));
                            if (entry == null) {
                                sender.sendMessage(ChatColor.RED + "Failed to add schedule.");
                                return true;
                            }
                            sender.sendMessage(ChatColor.GREEN + "Schedule added: " + entry.getId());
                            return true;
                        }
                        if (action.equals("remove")) {
                            if (!PermissionUtil.hasPermission(sender, "movietheatrecore.theatre.schedule.remove")) {
                                sender.sendMessage(configuration.insufficient_permissions());
                                return true;
                            }
                            if (filteredArgs.size() < 5) {
                                sender.sendMessage(ChatColor.RED + "/mtc theatre schedule remove <room> <index|id>");
                                return true;
                            }
                            TheatreRoom room = theatreManager.getRoom(filteredArgs.get(3));
                            if (room == null) {
                                sender.sendMessage(ChatColor.RED + "Unknown room: " + filteredArgs.get(3));
                                return true;
                            }
                            boolean removed = theatreManager.removeSchedule(room, filteredArgs.get(4));
                            if (!removed) {
                                sender.sendMessage(ChatColor.RED + "Schedule entry not found.");
                                return true;
                            }
                            sender.sendMessage(ChatColor.GREEN + "Schedule removed.");
                            return true;
                        }
                        if (action.equals("list")) {
                            if (!PermissionUtil.hasPermission(sender, "movietheatrecore.theatre.schedule.list")) {
                                sender.sendMessage(configuration.insufficient_permissions());
                                return true;
                            }
                            if (filteredArgs.size() < 4) {
                                sender.sendMessage(ChatColor.RED + "/mtc theatre schedule list <room>");
                                return true;
                            }
                            TheatreRoom room = theatreManager.getRoom(filteredArgs.get(3));
                            if (room == null) {
                                sender.sendMessage(ChatColor.RED + "Unknown room: " + filteredArgs.get(3));
                                return true;
                            }
                            List<ShowScheduleEntry> entries = theatreManager.getSchedules(room);
                            if (entries.isEmpty()) {
                                sender.sendMessage(ChatColor.YELLOW + "No schedules for room " + room.getName() + ".");
                                return true;
                            }
                            sender.sendMessage(ChatColor.GOLD + "Schedules for room " + room.getName() + ":");
                            int index = 1;
                            for (ShowScheduleEntry entry : entries) {
                                String nextRun = entry.getNextRun() == null ? "n/a" : entry.getNextRun().format(ShowScheduleEntry.FORMATTER);
                            sender.sendMessage(ChatColor.GRAY + "" + index + ") " + entry.getMediaId() + " at " + nextRun
                                    + " (" + entry.getRepeat().name().toLowerCase(Locale.ROOT) + ", enabled=" + entry.isEnabled() + ", id=" + entry.getId() + ")");
                                index++;
                            }
                            return true;
                        }
                        sendTheatreScheduleHelp(sender);
                        return true;
                    }
                    case "play" -> {
                        if (!PermissionUtil.hasPermission(sender, "movietheatrecore.theatre.play")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        if (filteredArgs.size() < 4) {
                            sender.sendMessage(ChatColor.RED + "/mtc theatre play <room> <mediaId>");
                            return true;
                        }
                        TheatreRoom room = theatreManager.getRoom(filteredArgs.get(2));
                        if (room == null) {
                            sender.sendMessage(ChatColor.RED + "Unknown room: " + filteredArgs.get(2));
                            return true;
                        }
                        theatreManager.playRoom(sender, room, filteredArgs.get(3));
                        return true;
                    }
                    case "stop" -> {
                        if (!PermissionUtil.hasPermission(sender, "movietheatrecore.theatre.stop")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        if (filteredArgs.size() < 3) {
                            sender.sendMessage(ChatColor.RED + "/mtc theatre stop <room>");
                            return true;
                        }
                        TheatreRoom room = theatreManager.getRoom(filteredArgs.get(2));
                        if (room == null) {
                            sender.sendMessage(ChatColor.RED + "Unknown room: " + filteredArgs.get(2));
                            return true;
                        }
                        theatreManager.stopShow(room);
                        sender.sendMessage(ChatColor.GREEN + "Stopped show in room " + room.getName() + ".");
                        return true;
                    }
                    case "doctor" -> {
                        if (!PermissionUtil.hasPermission(sender, "movietheatrecore.theatre.doctor")) {
                            sender.sendMessage(configuration.insufficient_permissions());
                            return true;
                        }
                        sender.sendMessage(ChatColor.GOLD + "MovieTheatreCore theatre doctor:");
                        for (String line : theatreManager.buildDoctorReport()) {
                            sender.sendMessage(line);
                        }
                        return true;
                    }
                    default -> {
                        sendTheatreHelp(sender);
                        return true;
                    }
                }
            }
            case "pack" -> {
                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.pack.manage")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 2) {
                    sendPackHelp(sender);
                    return true;
                }
                switch (filteredArgs.get(1).toLowerCase()) {
                    case "status" -> {
                        sendPackStatus(sender);
                        return true;
                    }
                    case "rebuild" -> {
                        sender.sendMessage(ChatColor.YELLOW + "Rebuilding resource pack...");
                        plugin.getAudioPackManager().rebuildPackAsync(sender);
                        return true;
                    }
                    case "url" -> {
                        sendPackUrl(sender);
                        return true;
                    }
                    default -> {
                        sendPackHelp(sender);
                        return true;
                    }
                }
            }
            case "stop" -> {
                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 2) {
                    sender.sendMessage(ChatColor.RED + "/mtc stop <screen>");
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
                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 2) {
                    sender.sendMessage(ChatColor.RED + "/mtc pause <screen>");
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
                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.playback")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 2) {
                    sender.sendMessage(ChatColor.RED + "/mtc resume <screen>");
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
                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.screen.manage")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                if (filteredArgs.size() < 3) {
                    sender.sendMessage(ChatColor.RED + "/mtc scale <screen> <fit|fill|stretch>");
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
                if (!PermissionUtil.hasPermission(sender, "movietheatrecore.admin")) {
                    sender.sendMessage(configuration.insufficient_permissions());
                    return true;
                }
                playbackManager.stopAll();
                screenManager.loadAll();
                TheatreManager theatreManager = plugin.getTheatreManager();
                if (theatreManager != null) {
                    theatreManager.reload();
                }
                sender.sendMessage(ChatColor.GREEN + "MovieTheatreCore reloaded.");
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
                List<String> candidates = List.of("screen", "media", "play", "stop", "pause", "resume", "scale", "reload", "diagnose", "update", "pack", "deps", "theatre");
                StringUtil.copyPartialMatches(args[0], candidates, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("screen")) {
                List<String> candidates = List.of("create", "delete", "list");
                StringUtil.copyPartialMatches(args[1], candidates, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("media")) {
                List<String> candidates = List.of("add", "remove", "list");
                StringUtil.copyPartialMatches(args[1], candidates, completions);
            } else if (args.length == 4 && args[0].equalsIgnoreCase("media") && args[1].equalsIgnoreCase("add")) {
                List<String> candidates = List.of("yt", "youtube");
                StringUtil.copyPartialMatches(args[3], candidates, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("pack")) {
                List<String> candidates = List.of("status", "rebuild", "url");
                StringUtil.copyPartialMatches(args[1], candidates, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("deps")) {
                List<String> candidates = List.of("status", "reinstall");
                StringUtil.copyPartialMatches(args[1], candidates, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("update")) {
                List<String> candidates = List.of("check");
                StringUtil.copyPartialMatches(args[1], candidates, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("theatre")) {
                List<String> candidates = List.of("room", "schedule", "play", "stop", "doctor");
                StringUtil.copyPartialMatches(args[1], candidates, completions);
            } else if (args.length == 2 && List.of("play", "stop", "pause", "resume", "scale").contains(args[0].toLowerCase())) {
                List<String> candidates = new ArrayList<>();
                for (Screen screen : plugin.getScreenManager().getScreens().values()) {
                    candidates.add(screen.getName());
                }
                StringUtil.copyPartialMatches(args[1], candidates, completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("theatre")) {
                if (args[1].equalsIgnoreCase("room")) {
                    List<String> candidates = List.of("create", "delete");
                    StringUtil.copyPartialMatches(args[2], candidates, completions);
                } else if (args[1].equalsIgnoreCase("schedule")) {
                    List<String> candidates = List.of("add", "remove", "list");
                    StringUtil.copyPartialMatches(args[2], candidates, completions);
                } else if (List.of("play", "stop").contains(args[1].toLowerCase())) {
                    List<String> candidates = new ArrayList<>();
                    TheatreManager theatreManager = plugin.getTheatreManager();
                    if (theatreManager != null) {
                        for (TheatreRoom room : theatreManager.getRooms().values()) {
                            candidates.add(room.getName());
                        }
                    }
                    StringUtil.copyPartialMatches(args[2], candidates, completions);
                }
            } else if (args.length == 4 && args[0].equalsIgnoreCase("theatre") && args[1].equalsIgnoreCase("play")) {
                List<String> candidates = new ArrayList<>();
                for (com._650a.movietheatrecore.media.MediaEntry entry : plugin.getMediaLibrary().listEntries()) {
                    candidates.add(entry.getName());
                }
                StringUtil.copyPartialMatches(args[3], candidates, completions);
            } else if (args.length == 4 && args[0].equalsIgnoreCase("theatre") && args[1].equalsIgnoreCase("schedule")) {
                List<String> candidates = new ArrayList<>();
                TheatreManager theatreManager = plugin.getTheatreManager();
                if (theatreManager != null) {
                    for (TheatreRoom room : theatreManager.getRooms().values()) {
                        candidates.add(room.getName());
                    }
                }
                StringUtil.copyPartialMatches(args[3], candidates, completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
                List<String> candidates = List.of("media", "url");
                StringUtil.copyPartialMatches(args[2], candidates, completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("scale")) {
                List<String> candidates = List.of("fit", "fill", "stretch");
                StringUtil.copyPartialMatches(args[2], candidates, completions);
            } else if (args.length == 4 && args[0].equalsIgnoreCase("play") && args[2].equalsIgnoreCase("media")) {
                List<String> candidates = new ArrayList<>();
                for (com._650a.movietheatrecore.media.MediaEntry entry : plugin.getMediaLibrary().listEntries()) {
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
        sender.sendMessage(ChatColor.GOLD + "MovieTheatreCore commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mtc screen create <name> <w> <h>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc screen delete <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc screen list");
        sender.sendMessage(ChatColor.YELLOW + "/mtc media add <name> <url> [--id <id>]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc media add <name> yt <videoId> [--id <id>]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc media remove <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc media list");
        sender.sendMessage(ChatColor.YELLOW + "/mtc play <screen> <source>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc play <screen> media <name> [--noaudio]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc play <screen> url <url> [--noaudio]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc stop <screen>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc pause <screen>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc resume <screen>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc scale <screen> <fit|fill|stretch>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc reload");
        sender.sendMessage(ChatColor.YELLOW + "/mtc diagnose");
        sender.sendMessage(ChatColor.YELLOW + "/mtc update check");
        sender.sendMessage(ChatColor.YELLOW + "/mtc pack status");
        sender.sendMessage(ChatColor.YELLOW + "/mtc pack rebuild");
        sender.sendMessage(ChatColor.YELLOW + "/mtc pack url");
        sender.sendMessage(ChatColor.YELLOW + "/mtc deps status");
        sender.sendMessage(ChatColor.YELLOW + "/mtc deps reinstall");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre room create <name> [screen...]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre room delete <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre schedule add <room> <HH:MM> <mediaId> [repeat=daily|weekly|none]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre schedule remove <room> <index|id>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre schedule list <room>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre play <room> <mediaId>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre stop <room>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre doctor");
    }

    private void sendScreenHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Screen commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mtc screen create <name> <w> <h>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc screen delete <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc screen list");
    }

    private void sendMediaHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Media commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mtc media add <name> <url> [--id <id>]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc media add <name> yt <videoId> [--id <id>]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc media remove <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc media list");
    }

    private void sendMediaAddHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage:");
        sender.sendMessage(ChatColor.YELLOW + "/mtc media add <name> <url> [--id <id>]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc media add <name> yt <videoId> [--id <id>]");
    }

    private void sendTheatreHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Theatre commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre room create <name> [screen...]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre room delete <name>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre schedule add <room> <HH:MM> <mediaId> [repeat=daily|weekly|none]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre schedule remove <room> <index|id>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre schedule list <room>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre play <room> <mediaId>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre stop <room>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre doctor");
    }

    private void sendTheatreRoomHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Theatre room commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre room create <name> [screen...]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre room delete <name>");
    }

    private void sendTheatreScheduleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Theatre schedule commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre schedule add <room> <HH:MM> <mediaId> [repeat=daily|weekly|none]");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre schedule remove <room> <index|id>");
        sender.sendMessage(ChatColor.YELLOW + "/mtc theatre schedule list <room>");
    }

    private void sendPackHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Pack commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mtc pack status");
        sender.sendMessage(ChatColor.YELLOW + "/mtc pack rebuild");
        sender.sendMessage(ChatColor.YELLOW + "/mtc pack url");
    }

    private void sendDepsHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Dependency commands:");
        sender.sendMessage(ChatColor.YELLOW + "/mtc deps status");
        sender.sendMessage(ChatColor.YELLOW + "/mtc deps reinstall");
    }

    private void sendDepsStatus(CommandSender sender) {
        DependencyManager dependencyManager = plugin.getDependencyManager();
        DependencyManager.EnvironmentInfo env = dependencyManager.getEnvironmentInfo();
        sender.sendMessage(ChatColor.GOLD + "MovieTheatreCore dependencies:");
        sender.sendMessage(ChatColor.GRAY + "OS/arch: " + env.getOs() + "/" + env.getArch());
        sender.sendMessage(ChatColor.GRAY + "Plugin dir executable: " + yesNo(env.isPluginDirExecutable()));
        sender.sendMessage(ChatColor.GRAY + "Install dir: " + env.getInstallDir());
        sender.sendMessage(ChatColor.GRAY + "Install dir writable: " + yesNo(env.isInstallDirWritable()));
        sender.sendMessage(ChatColor.GRAY + "Install dir executable: " + yesNo(env.isInstallDirExecutable()));

        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.FFMPEG, false), "ffmpeg");
        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.FFPROBE, false), "ffprobe");
        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.YT_DLP, false), "yt-dlp");
        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.DENO, false), "deno");

        String cookiesPath = configuration.youtube_cookies_path();
        if (cookiesPath == null || cookiesPath.isBlank()) {
            sender.sendMessage(ChatColor.GRAY + "Cookies file: not configured");
        } else {
            File cookies = new File(cookiesPath);
            sender.sendMessage(ChatColor.GRAY + "Cookies file: " + cookies.getPath() + " (readable=" + yesNo(cookies.exists() && cookies.canRead()) + ")");
        }
        sender.sendMessage(ChatColor.GRAY + "Require cookies: " + yesNo(configuration.youtube_require_cookies()));
    }

    private void sendPackStatus(CommandSender sender) {
        com._650a.movietheatrecore.audio.AudioPackManager packManager = plugin.getAudioPackManager();
        if (packManager == null) {
            sender.sendMessage(ChatColor.RED + "Pack manager not available.");
            return;
        }
        EmbeddedPackServer server = packManager.getPackServer();
        boolean enabled = configuration.resourcepack_server_enabled();
        boolean running = server.isRunning();
        String bind = configuration.resourcepack_server_bind() + ":" + configuration.resourcepack_server_port();
        String publicUrl = configuration.resourcepack_server_public_url();
        String packUrl = packManager.getPackUrl();
        String sha1 = packManager.getPackSha1();
        long lastBuild = packManager.getLastBuildMillis();

        sender.sendMessage(ChatColor.GOLD + "MovieTheatreCore pack status:");
        sender.sendMessage(ChatColor.GRAY + "Audio enabled: " + yesNo(configuration.audio_enabled()));
        sender.sendMessage(ChatColor.GRAY + "Server enabled: " + yesNo(enabled));
        sender.sendMessage(ChatColor.GRAY + "Server running: " + yesNo(running));
        sender.sendMessage(ChatColor.GRAY + "Bind: " + bind);
        sender.sendMessage(ChatColor.GRAY + "Public URL override: " + (publicUrl == null || publicUrl.isBlank() ? "none" : publicUrl));
        sender.sendMessage(ChatColor.GRAY + "Pack URL: " + (packUrl == null ? "n/a" : packUrl));
        sender.sendMessage(ChatColor.GRAY + "Pack SHA1: " + (sha1 == null || sha1.isBlank() ? "n/a" : sha1));
        sender.sendMessage(ChatColor.GRAY + "Last build: " + formatTimestamp(lastBuild));
        if (!running && server.getLastError() != null) {
            sender.sendMessage(ChatColor.RED + "Server error: " + server.getLastError());
        }
    }

    private void sendPackUrl(CommandSender sender) {
        com._650a.movietheatrecore.audio.AudioPackManager packManager = plugin.getAudioPackManager();
        if (packManager == null) {
            sender.sendMessage(ChatColor.RED + "Pack manager not available.");
            return;
        }
        String url = packManager.getPackUrl();
        String sha1 = packManager.getPackSha1();
        if (url == null || url.isBlank()) {
            sender.sendMessage(ChatColor.RED + "Pack URL not configured. Enable the internal server or set resource_pack.url.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Pack URL: " + url);
        sender.sendMessage(ChatColor.GREEN + "Pack SHA1: " + (sha1 == null || sha1.isBlank() ? "n/a" : sha1));
    }

    private void sendDiagnostics(CommandSender sender) {
        DependencyManager dependencyManager = plugin.getDependencyManager();
        DependencyManager.EnvironmentInfo env = dependencyManager.getEnvironmentInfo();
        sender.sendMessage(ChatColor.GOLD + "MovieTheatreCore diagnose:");
        sender.sendMessage(ChatColor.GRAY + "OS/arch: " + env.getOs() + "/" + env.getArch());
        sender.sendMessage(ChatColor.GRAY + "Plugin dir executable: " + yesNo(env.isPluginDirExecutable()));
        sender.sendMessage(ChatColor.GRAY + "Rootfs read-only: " + yesNo(env.isRootReadOnly()));
        sender.sendMessage(ChatColor.GRAY + "Install dir: " + env.getInstallDir());
        sender.sendMessage(ChatColor.GRAY + "Install dir writable: " + yesNo(env.isInstallDirWritable()));
        sender.sendMessage(ChatColor.GRAY + "Install dir executable: " + yesNo(env.isInstallDirExecutable()));

        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.FFMPEG, false), "ffmpeg");
        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.FFPROBE, false), "ffprobe");
        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.YT_DLP, false), "yt-dlp");
        reportBinary(sender, dependencyManager.resolveBinary(DependencyManager.BinaryType.DENO, false), "deno");

        String cookiesPath = configuration.youtube_cookies_path();
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
        if (resolved == null || !resolved.isValid() || resolved.getPath() == null) {
            sender.sendMessage(ChatColor.RED + label + ": missing");
            if (resolved != null && resolved.getError() != null) {
                sender.sendMessage(ChatColor.DARK_RED + "  " + resolved.getError());
            }
            return;
        }
        String source = resolved.getSource() == null ? "unknown" : resolved.getSource().name().toLowerCase(Locale.ROOT);
        sender.sendMessage(ChatColor.GRAY + label + ": " + resolved.getPath() + " (" + resolved.getVersion() + ", " + source + ")");
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String formatTimestamp(long value) {
        if (value <= 0) {
            return "n/a";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(value));
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

    private String resolveMediaAddUrl(CommandSender sender, List<String> args) {
        if (args.size() == 2) {
            return args.get(1);
        }
        if (args.size() == 3) {
            String source = args.get(1).toLowerCase(Locale.ROOT);
            String value = args.get(2);
            if (source.equals("yt") || source.equals("youtube")) {
                if (value == null || value.isBlank()) {
                    sender.sendMessage(ChatColor.RED + "Missing YouTube video ID.");
                    return null;
                }
                return "https://www.youtube.com/watch?v=" + value;
            }
            sender.sendMessage(ChatColor.RED + "Unknown media source: " + args.get(1) + ". Use a direct URL or 'yt <videoId>'.");
        }
        return null;
    }
}
