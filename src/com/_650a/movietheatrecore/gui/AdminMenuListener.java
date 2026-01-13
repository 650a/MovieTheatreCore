package com._650a.movietheatrecore.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.interfaces.Interfaces;
import com._650a.movietheatrecore.util.PermissionUtil;

public class AdminMenuListener implements Listener {

    private final Main plugin;
    private final Configuration configuration;
    private final Interfaces interfaces;

    public AdminMenuListener(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
        this.interfaces = new Interfaces();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getHolder() instanceof AdminMenuHolder) {
            event.setCancelled(true);
            handleAdminMenuClick(event);
            return;
        }
        if (inventory.getHolder() instanceof AdminSettingsHolder) {
            event.setCancelled(true);
            handleSettingsClick(event);
        }
    }

    private void handleAdminMenuClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String name = ChatColor.stripColor(meta.getDisplayName());
        if (name == null) {
            return;
        }
        if (name.equalsIgnoreCase("Media")) {
            openMedia(player);
        } else if (name.equalsIgnoreCase("Screens")) {
            openScreens(player);
        } else if (name.equalsIgnoreCase("Playback")) {
            openPlayback(player);
        } else if (name.equalsIgnoreCase("Settings")) {
            openSettings(player);
        }
    }

    private void handleSettingsClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (!PermissionUtil.hasPermission(player, "movietheatrecore.admin")) {
            player.sendMessage(configuration.insufficient_permissions());
            return;
        }

        String name = ChatColor.stripColor(meta.getDisplayName());
        if (name == null) {
            return;
        }
        if (name.equalsIgnoreCase("Reload plugin")) {
            Bukkit.dispatchCommand(player, "mtc reload");
        } else if (name.equalsIgnoreCase("Dependency status")) {
            Bukkit.dispatchCommand(player, "mtc deps status");
        } else if (name.equalsIgnoreCase("Pack status")) {
            Bukkit.dispatchCommand(player, "mtc pack status");
        }
    }

    private void openMedia(Player player) {
        if (!PermissionUtil.hasPermission(player, "movietheatrecore.media.manage")) {
            player.sendMessage(configuration.insufficient_permissions());
            return;
        }
        if (!plugin.getGuiSupport().isAvailable()) {
            plugin.getVideosPages().put(player.getUniqueId(), 0);
            player.openInventory(interfaces.getVideos(0));
            return;
        }
        new MediaLibraryMenu(plugin, null).open(player);
    }

    private void openScreens(Player player) {
        if (!PermissionUtil.hasPermission(player, "movietheatrecore.screen.manage")) {
            player.sendMessage(configuration.insufficient_permissions());
            return;
        }
        if (!plugin.getGuiSupport().isAvailable()) {
            plugin.getScreensPages().put(player.getUniqueId(), 0);
            player.openInventory(interfaces.getScreens(0));
            return;
        }
        new ScreenListMenu(plugin).open(player);
    }

    private void openPlayback(Player player) {
        if (!PermissionUtil.hasPermission(player, "movietheatrecore.playback")) {
            player.sendMessage(configuration.insufficient_permissions());
            return;
        }
        plugin.getGuiSupport().openScreenManager(player);
    }

    private void openSettings(Player player) {
        if (!PermissionUtil.hasPermission(player, "movietheatrecore.admin")) {
            player.sendMessage(configuration.insufficient_permissions());
            return;
        }
        new AdminSettingsMenu(plugin).open(player);
    }
}
