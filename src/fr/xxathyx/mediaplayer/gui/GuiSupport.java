package fr.xxathyx.mediaplayer.gui;

import org.bukkit.entity.Player;

public interface GuiSupport {

    boolean isAvailable();

    void register();

    void openScreenManager(Player player);
}
