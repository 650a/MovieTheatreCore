package com._650a.movietheatrecore.gui;

import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.media.MediaEntry;
import com._650a.movietheatrecore.media.MediaLibrary;
import com._650a.movietheatrecore.playback.PlaybackManager;
import com._650a.movietheatrecore.render.ScalingMode;
import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.screen.ScreenManager;
import com._650a.movietheatrecore.util.PermissionUtil;
import com._650a.movietheatrecore.video.Video;

public class GuiListener implements Listener {

    private final Main plugin;
    private final Configuration configuration;

    public GuiListener(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();

        if (inventory.getHolder() instanceof ScreenManagerHolder) {
            event.setCancelled(true);
            handleScreenManagerClick(event);
            return;
        }

        if (inventory.getHolder() instanceof ScreenListHolder) {
            event.setCancelled(true);
            handleScreenListClick(event);
            return;
        }

        if (inventory.getHolder() instanceof ScreenDetailHolder) {
            event.setCancelled(true);
            handleScreenDetailClick(event);
            return;
        }

        if (inventory.getHolder() instanceof MediaLibraryHolder) {
            event.setCancelled(true);
            handleMediaLibraryClick(event);
            return;
        }

        if (inventory.getHolder() instanceof NowPlayingHolder) {
            event.setCancelled(true);
            handleNowPlayingClick(event);
        }
    }

    private void handleScreenManagerClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey screenKey = new NamespacedKey(plugin, "screen-id");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String screenId = container.get(screenKey, PersistentDataType.STRING);
        if (screenId == null) {
            return;
        }

        ScreenManager screenManager = plugin.getScreenManager();
        Screen screen = screenManager.getScreen(UUID.fromString(screenId));
        if (screen == null) {
            ((Player) event.getWhoClicked()).sendMessage(ChatColor.RED + "Screen no longer exists.");
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (!PermissionUtil.hasPermission(player, "movietheatrecore.screen.manage")) {
            player.sendMessage(configuration.insufficient_permissions());
            return;
        }
        new NowPlayingMenu(plugin, screen).open(player);
    }

    private void handleNowPlayingClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
        NamespacedKey screenKey = new NamespacedKey(plugin, "screen-id");
        NamespacedKey scaleKey = new NamespacedKey(plugin, "scale-mode");
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String action = container.get(actionKey, PersistentDataType.STRING);
        String screenId = container.get(screenKey, PersistentDataType.STRING);
        if (action == null || screenId == null) {
            return;
        }

        ScreenManager screenManager = plugin.getScreenManager();
        PlaybackManager playbackManager = plugin.getPlaybackManager();
        Screen screen = screenManager.getScreen(UUID.fromString(screenId));
        if (screen == null) {
            ((Player) event.getWhoClicked()).sendMessage(ChatColor.RED + "Screen no longer exists.");
            return;
        }

        Player player = (Player) event.getWhoClicked();

        switch (action) {
            case "play" -> {
                if (!PermissionUtil.hasPermission(player, "movietheatrecore.playback")) {
                    player.sendMessage(configuration.insufficient_permissions());
                    return;
                }
                handlePlay(player, playbackManager, screen);
            }
            case "stop" -> {
                if (!PermissionUtil.hasPermission(player, "movietheatrecore.playback")) {
                    player.sendMessage(configuration.insufficient_permissions());
                    return;
                }
                playbackManager.stop(screen, null, true);
            }
            case "pause" -> {
                if (!PermissionUtil.hasPermission(player, "movietheatrecore.playback")) {
                    player.sendMessage(configuration.insufficient_permissions());
                    return;
                }
                playbackManager.pause(screen);
            }
            case "resume" -> {
                if (!PermissionUtil.hasPermission(player, "movietheatrecore.playback")) {
                    player.sendMessage(configuration.insufficient_permissions());
                    return;
                }
                playbackManager.resume(screen);
            }
            case "scale" -> {
                if (!PermissionUtil.hasPermission(player, "movietheatrecore.screen.manage")) {
                    player.sendMessage(configuration.insufficient_permissions());
                    return;
                }
                String modeRaw = container.get(scaleKey, PersistentDataType.STRING);
                if (modeRaw != null) {
                    ScalingMode mode = ScalingMode.valueOf(modeRaw);
                    screen.setScaleMode(mode);
                    player.sendMessage(ChatColor.GREEN + "Scale mode set to " + mode.name() + ".");
                    new NowPlayingMenu(plugin, screen).open(player);
                }
            }
            default -> {
            }
        }
    }

    private void handleScreenListClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
        NamespacedKey screenKey = new NamespacedKey(plugin, "screen-id");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String action = container.get(actionKey, PersistentDataType.STRING);
        String screenId = container.get(screenKey, PersistentDataType.STRING);

        Player player = (Player) event.getWhoClicked();
        if ("create-screen".equals(action)) {
            if (!PermissionUtil.hasPermission(player, "movietheatrecore.screen.manage")) {
                player.sendMessage(configuration.insufficient_permissions());
                return;
            }
            AdminWizardListener.startScreenWizard(player);
            return;
        }
        if (screenId == null) {
            return;
        }
        Screen screen;
        try {
            screen = plugin.getScreenManager().getScreen(UUID.fromString(screenId));
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid screen reference.");
            return;
        }
        if (screen == null) {
            player.sendMessage(ChatColor.RED + "Screen no longer exists.");
            return;
        }
        if (!PermissionUtil.hasPermission(player, "movietheatrecore.screen.manage")) {
            player.sendMessage(configuration.insufficient_permissions());
            return;
        }
        new ScreenDetailMenu(plugin, screen).open(player);
    }

    private void handleScreenDetailClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
        NamespacedKey screenKey = new NamespacedKey(plugin, "screen-id");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String action = container.get(actionKey, PersistentDataType.STRING);
        String screenId = container.get(screenKey, PersistentDataType.STRING);
        if (action == null || screenId == null) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Screen screen;
        try {
            screen = plugin.getScreenManager().getScreen(UUID.fromString(screenId));
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid screen reference.");
            return;
        }
        if (screen == null) {
            player.sendMessage(ChatColor.RED + "Screen no longer exists.");
            return;
        }
        switch (action) {
            case "assign-media" -> {
                if (!PermissionUtil.hasPermission(player, "movietheatrecore.screen.manage")) {
                    player.sendMessage(configuration.insufficient_permissions());
                    return;
                }
                new MediaLibraryMenu(plugin, screen).open(player);
            }
            case "delete-screen" -> {
                if (!PermissionUtil.hasPermission(player, "movietheatrecore.screen.manage")) {
                    player.sendMessage(configuration.insufficient_permissions());
                    return;
                }
                plugin.getScreenManager().deleteScreen(screen);
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Screen deleted: " + screen.getName());
            }
            case "playback" -> {
                if (!PermissionUtil.hasPermission(player, "movietheatrecore.playback")) {
                    player.sendMessage(configuration.insufficient_permissions());
                    return;
                }
                new NowPlayingMenu(plugin, screen).open(player);
            }
            case "back" -> new ScreenListMenu(plugin).open(player);
            default -> {
            }
        }
    }

    private void handleMediaLibraryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
        NamespacedKey mediaKey = new NamespacedKey(plugin, "media-name");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String action = container.get(actionKey, PersistentDataType.STRING);
        String mediaName = container.get(mediaKey, PersistentDataType.STRING);
        Player player = (Player) event.getWhoClicked();

        if ("add-media".equals(action)) {
            if (!PermissionUtil.hasPermission(player, "movietheatrecore.media.admin")) {
                player.sendMessage(configuration.insufficient_permissions());
                return;
            }
            AdminWizardListener.startMediaWizard(player);
            return;
        }

        MediaLibraryHolder holder = (MediaLibraryHolder) event.getInventory().getHolder();
        if (mediaName == null) {
            return;
        }

        MediaLibrary library = plugin.getMediaLibrary();
        MediaEntry entry = library.getEntry(mediaName);
        if (entry == null) {
            player.sendMessage(ChatColor.RED + "Media not found: " + mediaName);
            return;
        }

        if (holder != null && holder.getScreenId() != null) {
            Screen screen = plugin.getScreenManager().getScreen(holder.getScreenId());
            if (screen == null) {
                player.sendMessage(ChatColor.RED + "Screen no longer exists.");
                return;
            }
            if (!PermissionUtil.hasPermission(player, "movietheatrecore.screen.manage")) {
                player.sendMessage(configuration.insufficient_permissions());
                return;
            }
            try {
                screen.setVideoName(entry.getName());
                player.sendMessage(ChatColor.GREEN + "Assigned " + entry.getName() + " to " + screen.getName() + ".");
                player.closeInventory();
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Failed to assign media: " + e.getMessage());
            }
            return;
        }
        player.sendMessage(ChatColor.GRAY + "Media: " + entry.getName() + " (" + entry.getUrl() + ")");
    }

    private void handlePlay(Player player, PlaybackManager playbackManager, Screen screen) {
        String videoName = screen.getVideoName();
        if (videoName == null || videoName.equalsIgnoreCase("none")) {
            player.sendMessage(ChatColor.RED + "No video linked to this screen. Use /mtc play <screen> <source>.");
            return;
        }

        MediaEntry mediaEntry = plugin.getMediaLibrary().getEntry(videoName);
        if (mediaEntry != null) {
            plugin.getMediaManager().playMedia(player, screen, mediaEntry.getName());
            return;
        }

        Video video = null;
        for (Video candidate : plugin.getRegisteredVideos()) {
            if (candidate.getName().equalsIgnoreCase(videoName)) {
                video = candidate;
                break;
            }
        }

        if (video == null) {
            player.sendMessage(ChatColor.RED + "Video not found: " + videoName);
            return;
        }

        playbackManager.start(screen, video);
        player.sendMessage(ChatColor.GREEN + "Playing " + video.getName() + " on " + screen.getName() + ".");
    }
}
