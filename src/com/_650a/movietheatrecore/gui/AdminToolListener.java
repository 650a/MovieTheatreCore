package com._650a.movietheatrecore.gui;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.items.ItemStacks;
import com._650a.movietheatrecore.util.PermissionUtil;

public class AdminToolListener implements Listener {

    private final Main plugin;
    private final Configuration configuration;
    private final ItemStacks itemStacks = new ItemStacks();

    public AdminToolListener(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!itemStacks.isAdminTool(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!PermissionUtil.hasPermission(player, "movietheatrecore.admin")) {
            player.sendMessage(configuration.insufficient_permissions());
            return;
        }
        new AdminMenu(plugin).open(player);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (itemStacks.isAdminTool(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.YELLOW + "The MovieTheatreCore admin tool cannot be dropped.");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (!itemStacks.isAdminTool(current) && !itemStacks.isAdminTool(cursor)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (itemStacks.isAdminTool(event.getOldCursor()) || itemStacks.isAdminTool(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack item : event.getNewItems().values()) {
            if (itemStacks.isAdminTool(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (PermissionUtil.hasPermission(player, "movietheatrecore.admin")) {
            return;
        }
        removeAdminTool(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeAdminTool(event.getPlayer());
    }

    private void removeAdminTool(Player player) {
        if (player == null || player.getInventory() == null) {
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (itemStacks.isAdminTool(item)) {
                player.getInventory().remove(item);
            }
        }
    }
}
