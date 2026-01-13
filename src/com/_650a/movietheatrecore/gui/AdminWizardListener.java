package com._650a.movietheatrecore.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.media.MediaLibrary;
import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.screen.ScreenManager;
import com._650a.movietheatrecore.util.PermissionUtil;

public class AdminWizardListener implements Listener {

    private static final Map<UUID, WizardSession> sessions = new ConcurrentHashMap<>();

    private final Main plugin;
    private final Configuration configuration;

    public AdminWizardListener(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
    }

    public static void startScreenWizard(Player player) {
        sessions.put(player.getUniqueId(), new WizardSession(WizardType.SCREEN));
        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "Screen wizard: enter a screen name (or type 'cancel').");
    }

    public static void startMediaWizard(Player player) {
        sessions.put(player.getUniqueId(), new WizardSession(WizardType.MEDIA));
        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "Media wizard: enter a media name (or type 'cancel').");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        WizardSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);

        String message = event.getMessage() == null ? "" : event.getMessage().trim();
        if (message.equalsIgnoreCase("cancel")) {
            sessions.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(ChatColor.YELLOW + "Wizard cancelled."));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> handleWizardInput(player, session, message));
    }

    private void handleWizardInput(Player player, WizardSession session, String message) {
        if (session.type == WizardType.SCREEN) {
            handleScreenWizard(player, session, message);
            return;
        }
        if (session.type == WizardType.MEDIA) {
            handleMediaWizard(player, session, message);
        }
    }

    private void handleScreenWizard(Player player, WizardSession session, String message) {
        if (!PermissionUtil.hasPermission(player, "movietheatrecore.screen.manage")) {
            player.sendMessage(configuration.insufficient_permissions());
            sessions.remove(player.getUniqueId());
            return;
        }

        ScreenManager screenManager = plugin.getScreenManager();
        if (session.step == 0) {
            if (message.isBlank()) {
                player.sendMessage(ChatColor.RED + "Screen name cannot be empty.");
                return;
            }
            Screen existing = screenManager.getScreenByName(message);
            if (existing != null) {
                player.sendMessage(ChatColor.RED + "A screen named " + message + " already exists.");
                return;
            }
            session.name = message;
            session.step = 1;
            player.sendMessage(ChatColor.GOLD + "Enter screen width (blocks).");
            return;
        }
        if (session.step == 1) {
            int width = parsePositiveInt(player, message, "width");
            if (width <= 0) {
                return;
            }
            session.width = width;
            session.step = 2;
            player.sendMessage(ChatColor.GOLD + "Enter screen height (blocks).");
            return;
        }
        if (session.step == 2) {
            int height = parsePositiveInt(player, message, "height");
            if (height <= 0) {
                return;
            }
            Screen created = screenManager.createScreen(player, session.name, session.width, height);
            if (created == null) {
                player.sendMessage(ChatColor.RED + "Failed to create screen. Check placement space and try again.");
            } else {
                player.sendMessage(ChatColor.GREEN + "Screen created: " + created.getName() + " (" + created.getWidth() + "x" + created.getHeight() + ").");
            }
            sessions.remove(player.getUniqueId());
        }
    }

    private void handleMediaWizard(Player player, WizardSession session, String message) {
        if (!PermissionUtil.hasPermission(player, "movietheatrecore.media.admin")) {
            player.sendMessage(configuration.insufficient_permissions());
            sessions.remove(player.getUniqueId());
            return;
        }

        MediaLibrary library = plugin.getMediaLibrary();
        if (session.step == 0) {
            if (message.isBlank()) {
                player.sendMessage(ChatColor.RED + "Media name cannot be empty.");
                return;
            }
            if (library.getEntry(message) != null) {
                player.sendMessage(ChatColor.RED + "Media named " + message + " already exists.");
                return;
            }
            session.name = message;
            session.step = 1;
            player.sendMessage(ChatColor.GOLD + "Enter a media URL (or 'yt <videoId>').");
            return;
        }
        if (session.step == 1) {
            String url = message;
            if (message.toLowerCase().startsWith("yt ")) {
                url = "https://www.youtube.com/watch?v=" + message.substring(3).trim();
            } else if (message.toLowerCase().startsWith("youtube ")) {
                url = "https://www.youtube.com/watch?v=" + message.substring(8).trim();
            }
            plugin.getMediaManager().addMedia(player, session.name, url, null);
            player.sendMessage(ChatColor.GREEN + "Media added: " + session.name + " (processing in background).");
            sessions.remove(player.getUniqueId());
        }
    }

    private int parsePositiveInt(Player player, String value, String label) {
        try {
            int number = Integer.parseInt(value);
            if (number <= 0) {
                player.sendMessage(ChatColor.RED + "Screen " + label + " must be at least 1.");
                return -1;
            }
            return number;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid " + label + ": " + value);
            return -1;
        }
    }

    private static final class WizardSession {
        private final WizardType type;
        private int step = 0;
        private String name;
        private int width;

        private WizardSession(WizardType type) {
            this.type = type;
        }
    }

    private enum WizardType {
        SCREEN,
        MEDIA
    }
}
