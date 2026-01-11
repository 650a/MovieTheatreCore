package fr.xxathyx.mediaplayer.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class NowPlayingHolder implements InventoryHolder {

    private final UUID screenId;
    private Inventory inventory;

    public NowPlayingHolder(UUID screenId) {
        this.screenId = screenId;
    }

    public UUID getScreenId() {
        return screenId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
