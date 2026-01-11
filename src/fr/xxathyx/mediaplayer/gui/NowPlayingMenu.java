package fr.xxathyx.mediaplayer.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.render.ScalingMode;
import fr.xxathyx.mediaplayer.screen.Screen;

public class NowPlayingMenu {

    private final Main plugin;
    private final Screen screen;

    public NowPlayingMenu(Main plugin, Screen screen) {
        this.plugin = plugin;
        this.screen = screen;
    }

    public void open(Player player) {
        player.openInventory(build());
    }

    public Inventory build() {
        NowPlayingHolder holder = new NowPlayingHolder(screen.getUUID());
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_PURPLE + "Now Playing: " + screen.getName());
        holder.setInventory(inventory);

        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
        NamespacedKey screenKey = new NamespacedKey(plugin, "screen-id");
        NamespacedKey scaleKey = new NamespacedKey(plugin, "scale-mode");

        inventory.setItem(10, actionItem(Material.LIME_DYE, ChatColor.GREEN + "Play", "play", actionKey, screenKey));
        inventory.setItem(11, actionItem(Material.RED_DYE, ChatColor.RED + "Stop", "stop", actionKey, screenKey));
        inventory.setItem(12, actionItem(Material.YELLOW_DYE, ChatColor.YELLOW + "Pause", "pause", actionKey, screenKey));
        inventory.setItem(13, actionItem(Material.GREEN_DYE, ChatColor.GREEN + "Resume", "resume", actionKey, screenKey));

        inventory.setItem(15, scaleItem(ChatColor.AQUA + "Scale: FIT", ScalingMode.FIT, actionKey, screenKey, scaleKey));
        inventory.setItem(16, scaleItem(ChatColor.AQUA + "Scale: FILL", ScalingMode.FILL, actionKey, screenKey, scaleKey));
        inventory.setItem(17, scaleItem(ChatColor.AQUA + "Scale: STRETCH", ScalingMode.STRETCH, actionKey, screenKey, scaleKey));

        return inventory;
    }

    private ItemStack actionItem(Material material, String name, String action, NamespacedKey actionKey, NamespacedKey screenKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(screenKey, PersistentDataType.STRING, screen.getUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack scaleItem(String name, ScalingMode mode, NamespacedKey actionKey, NamespacedKey screenKey, NamespacedKey scaleKey) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current: " + screen.getScaleMode().name());
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "scale");
        meta.getPersistentDataContainer().set(screenKey, PersistentDataType.STRING, screen.getUUID().toString());
        meta.getPersistentDataContainer().set(scaleKey, PersistentDataType.STRING, mode.name());
        item.setItemMeta(meta);
        return item;
    }
}
