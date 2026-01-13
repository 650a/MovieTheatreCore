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
import com._650a.movietheatrecore.screen.Screen;

public class ScreenListMenu {

    private final Main plugin;

    public ScreenListMenu(Main plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        player.openInventory(build());
    }

    public Inventory build() {
        ScreenListHolder holder = new ScreenListHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_PURPLE + "MovieTheatreCore Screens");
        holder.setInventory(inventory);

        NamespacedKey screenKey = new NamespacedKey(plugin, "screen-id");
        NamespacedKey actionKey = new NamespacedKey(plugin, "action");

        ItemStack create = new ItemStack(Material.ANVIL);
        ItemMeta createMeta = create.getItemMeta();
        createMeta.setDisplayName(ChatColor.GREEN + "Create Screen");
        createMeta.setLore(List.of(ChatColor.GRAY + "Click to start the screen wizard."));
        createMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "create-screen");
        create.setItemMeta(createMeta);
        inventory.setItem(53, create);

        int slot = 0;
        for (Screen screen : plugin.getScreenManager().getScreens().values()) {
            if (slot >= 53) {
                break;
            }
            ItemStack item = new ItemStack(Material.ITEM_FRAME);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + screen.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Size: " + screen.getWidth() + "x" + screen.getHeight());
            lore.add(ChatColor.DARK_GRAY + "Click to manage");
            meta.setLore(lore);
            if (screen.getUUID() != null) {
                meta.getPersistentDataContainer().set(screenKey, PersistentDataType.STRING, screen.getUUID().toString());
            }
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }

        return inventory;
    }
}
