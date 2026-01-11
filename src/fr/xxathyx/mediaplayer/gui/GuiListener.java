package fr.xxathyx.mediaplayer.gui;

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

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.playback.PlaybackManager;
import fr.xxathyx.mediaplayer.render.ScalingMode;
import fr.xxathyx.mediaplayer.screen.Screen;
import fr.xxathyx.mediaplayer.screen.ScreenManager;
import fr.xxathyx.mediaplayer.video.Video;

public class GuiListener implements Listener {

    private final Main plugin;

    public GuiListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();

        if (inventory.getHolder() instanceof ScreenManagerHolder) {
            event.setCancelled(true);
            handleScreenManagerClick(event);
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
            return;
        }

        Player player = (Player) event.getWhoClicked();
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
            return;
        }

        Player player = (Player) event.getWhoClicked();

        switch (action) {
            case "play" -> handlePlay(player, playbackManager, screen);
            case "stop" -> playbackManager.stop(screen, null);
            case "pause" -> playbackManager.pause(screen);
            case "resume" -> playbackManager.resume(screen);
            case "scale" -> {
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

    private void handlePlay(Player player, PlaybackManager playbackManager, Screen screen) {
        String videoName = screen.getVideoName();
        if (videoName == null || videoName.equalsIgnoreCase("none")) {
            player.sendMessage(ChatColor.RED + "No video linked to this screen. Use /mp play <screen> <source>.");
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
