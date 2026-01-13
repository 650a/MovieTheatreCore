package com._650a.movietheatrecore.gui;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.screen.Screen;

public class ScreenDetailMenu {

    private final Main plugin;
    private final Screen screen;

    public ScreenDetailMenu(Main plugin, Screen screen) {
        this.plugin = plugin;
        this.screen = screen;
    }

    public void open(Player player) {
        player.openInventory(build());
    }

    public Inventory build() {
        ScreenDetailHolder holder = new ScreenDetailHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_PURPLE + "Screen: " + screen.getName());
        holder.setInventory(inventory);

        NamespacedKey screenKey = new NamespacedKey(plugin, "screen-id");
        NamespacedKey actionKey = new NamespacedKey(plugin, "action");

        inventory.setItem(10, actionItem(Material.BOOK, ChatColor.AQUA + "Assign Media",
                "assign-media", actionKey, screenKey, "Choose media for this screen."));
        inventory.setItem(12, actionItem(Material.REDSTONE_TORCH, ChatColor.AQUA + "Playback Controls",
                "playback", actionKey, screenKey, "Play, pause, or stop."));
        inventory.setItem(14, actionItem(Material.BARRIER, ChatColor.RED + "Delete Screen",
                "delete-screen", actionKey, screenKey, "Remove all frames and data."));
        inventory.setItem(16, actionItem(Material.ARROW, ChatColor.GRAY + "Back",
                "back", actionKey, screenKey, "Return to screen list."));

        return inventory;
    }

    private ItemStack actionItem(Material material, String name, String action, NamespacedKey actionKey, NamespacedKey screenKey, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(ChatColor.GRAY + lore));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (screen.getUUID() != null) {
            meta.getPersistentDataContainer().set(screenKey, PersistentDataType.STRING, screen.getUUID().toString());
        }
        item.setItemMeta(meta);
        return item;
    }
}
