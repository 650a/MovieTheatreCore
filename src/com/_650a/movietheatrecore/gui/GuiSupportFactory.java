package com._650a.movietheatrecore.gui;

import com._650a.movietheatrecore.Main;

public final class GuiSupportFactory {

    private GuiSupportFactory() {
    }

    public static GuiSupport create(Main plugin) {
        if (isPersistentDataSupported()) {
            return new ModernGuiSupport(plugin);
        }
        return new LegacyGuiSupport(plugin);
    }

    private static boolean isPersistentDataSupported() {
        try {
            Class.forName("org.bukkit.persistence.PersistentDataContainer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
