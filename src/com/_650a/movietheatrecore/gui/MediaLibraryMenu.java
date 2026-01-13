package com._650a.movietheatrecore.gui;

import java.util.ArrayList;
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
import com._650a.movietheatrecore.media.MediaEntry;
import com._650a.movietheatrecore.screen.Screen;

public class MediaLibraryMenu {

    private final Main plugin;
    private final Screen screen;

    public MediaLibraryMenu(Main plugin, Screen screen) {
        this.plugin = plugin;
        this.screen = screen;
    }

    public void open(Player player) {
        player.openInventory(build());
    }

    public Inventory build() {
        String title = screen == null ? "MovieTheatreCore Media" : "Assign Media: " + screen.getName();
        MediaLibraryHolder holder = new MediaLibraryHolder(screen == null ? null : screen.getUUID());
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_PURPLE + title);
        holder.setInventory(inventory);

        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
        NamespacedKey mediaKey = new NamespacedKey(plugin, "media-name");

        ItemStack add = new ItemStack(Material.HOPPER);
        ItemMeta addMeta = add.getItemMeta();
        addMeta.setDisplayName(ChatColor.GREEN + "Add Media");
        addMeta.setLore(List.of(ChatColor.GRAY + "Click to add a new URL."));
        addMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "add-media");
        add.setItemMeta(addMeta);
        inventory.setItem(53, add);

        int slot = 0;
        for (MediaEntry entry : plugin.getMediaLibrary().listEntries()) {
            if (slot >= 53) {
                break;
            }
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + entry.getName());
            List<String> lore = new ArrayList<>();
            if (screen != null) {
                lore.add(ChatColor.GRAY + "Click to assign");
            } else {
                lore.add(ChatColor.GRAY + "URL: " + entry.getUrl());
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(mediaKey, PersistentDataType.STRING, entry.getName());
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }

        return inventory;
    }
}
