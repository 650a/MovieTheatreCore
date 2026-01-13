package com._650a.movietheatrecore.util;

import org.bukkit.command.CommandSender;

public final class PermissionUtil {

    private PermissionUtil() {
    }

    public static boolean hasPermission(CommandSender sender, String currentNode) {
        return sender.hasPermission(currentNode);
    }
}
