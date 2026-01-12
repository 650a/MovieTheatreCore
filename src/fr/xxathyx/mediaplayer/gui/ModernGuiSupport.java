package fr.xxathyx.mediaplayer.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import fr.xxathyx.mediaplayer.Main;

public class ModernGuiSupport implements GuiSupport {

    private final Main plugin;

    public ModernGuiSupport(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void register() {
        Bukkit.getServer().getPluginManager().registerEvents(new GuiListener(plugin), plugin);
    }

    @Override
    public void openScreenManager(Player player) {
        new ScreenManagerMenu(plugin).open(player);
    }
}
