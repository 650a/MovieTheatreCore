package com._650a.movietheatrecore.gui;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.screen.Screen;

public class LegacyGuiSupport implements GuiSupport {

    private final Main plugin;

    public LegacyGuiSupport(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void register() {
        // Legacy servers do not support the persistent data GUI. Nothing to register.
    }

    @Override
    public void openScreenManager(Player player) {
        player.sendMessage(ChatColor.YELLOW + "The screen manager GUI is not available on this server version.");
        player.sendMessage(ChatColor.GOLD + "Screens:");
        for (Screen screen : plugin.getScreenManager().getScreens().values()) {
            player.sendMessage(ChatColor.GRAY + "- " + screen.getName() + " (" + screen.getWidth() + "x" + screen.getHeight() + ")");
        }
    }
}
