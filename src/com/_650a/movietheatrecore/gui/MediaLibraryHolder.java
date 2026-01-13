package com._650a.movietheatrecore.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MediaLibraryHolder implements InventoryHolder {

    private final UUID screenId;
    private Inventory inventory;

    public MediaLibraryHolder(UUID screenId) {
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
