package com._650a.movietheatrecore.gui;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com._650a.movietheatrecore.Main;

public class AdminSettingsMenu {

    private final Main plugin;

    public AdminSettingsMenu(Main plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        player.openInventory(build());
    }

    public Inventory build() {
        AdminSettingsHolder holder = new AdminSettingsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_PURPLE + "MovieTheatreCore Settings");
        holder.setInventory(inventory);

        inventory.setItem(11, menuItem(Material.REPEATER, ChatColor.GREEN + "Reload plugin", "Reload screens and theatre data."));
        inventory.setItem(13, menuItem(Material.BOOK, ChatColor.GOLD + "Dependency status", "Show dependency health in chat."));
        inventory.setItem(15, menuItem(Material.NOTE_BLOCK, ChatColor.AQUA + "Pack status", "Show resource pack status in chat."));

        return inventory;
    }

    private ItemStack menuItem(Material material, String name, String loreText) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(ChatColor.GRAY + loreText));
        item.setItemMeta(meta);
        return item;
    }
}
