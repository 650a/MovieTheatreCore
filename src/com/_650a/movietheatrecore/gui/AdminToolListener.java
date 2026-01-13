package com._650a.movietheatrecore.gui;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
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
        if (!ensureOwnership(player, item)) {
            return;
        }
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
        Player player = event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : null;
        if (player != null) {
            if (!ensureOwnership(player, current) || !ensureOwnership(player, cursor)) {
                event.setCancelled(true);
                return;
            }
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
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (itemStacks.isAdminTool(event.getItem())) {
            event.setCancelled(true);
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
        boolean hasAdmin = PermissionUtil.hasPermission(player, "movietheatrecore.admin");
        for (ItemStack item : player.getInventory().getContents()) {
            if (itemStacks.isAdminTool(item) && (!hasAdmin || !itemStacks.isAdminToolFor(item, player))) {
                player.getInventory().remove(item);
            }
        }
    }

    private boolean ensureOwnership(Player player, ItemStack item) {
        if (!itemStacks.isAdminTool(item)) {
            return true;
        }
        java.util.UUID owner = itemStacks.getAdminToolOwner(item);
        if (owner == null) {
            itemStacks.bindAdminTool(item, player);
            return true;
        }
        if (!owner.equals(player.getUniqueId())) {
            player.getInventory().remove(item);
            player.sendMessage(ChatColor.RED + "This admin wand is bound to another player.");
            return false;
        }
        return true;
    }
}
