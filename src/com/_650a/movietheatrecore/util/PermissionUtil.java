package com._650a.movietheatrecore.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public final class PermissionUtil {

    private static final Set<String> warnedLegacy = ConcurrentHashMap.newKeySet();

    private PermissionUtil() {
    }

    public static boolean hasPermission(CommandSender sender, String currentNode, String legacyNode) {
        if (sender.hasPermission(currentNode)) {
            return true;
        }
        if (legacyNode != null && sender.hasPermission(legacyNode)) {
            warnLegacy(legacyNode, currentNode);
            return true;
        }
        return false;
    }

    private static void warnLegacy(String legacyNode, String currentNode) {
        String key = legacyNode + "->" + currentNode;
        if (warnedLegacy.add(key)) {
            Bukkit.getLogger().warning("[MovieTheatreCore]: Legacy permission '" + legacyNode
                    + "' detected; please migrate to '" + currentNode + "'.");
        }
    }
}
