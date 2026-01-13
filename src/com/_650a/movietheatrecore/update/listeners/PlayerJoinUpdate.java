package com._650a.movietheatrecore.update.listeners;

import java.io.IOException;
import java.net.UnknownHostException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.group.Group;
import com._650a.movietheatrecore.notification.Notification;
import com._650a.movietheatrecore.notification.NotificationType;
import com._650a.movietheatrecore.update.Updater;
import com._650a.movietheatrecore.util.PermissionUtil;

/** 
* The PlayerJoinUpdate class implements {@link Listener}, it consist
* of a single event method {@link #onJoin(PlayerJoinEvent)}.
*
* @author  hwic
* @version 1.0.0
* @since   2021-08-23 
*/

public class PlayerJoinUpdate implements Listener {
	
	private final Main plugin = Main.getPlugin(Main.class);
	
	private final Configuration configuration = new Configuration();
	private final Updater updater = new Updater();
	
    /**
     * Called whenever a {@link Player} having movietheatrecore.permission.admin permission joins
     * the server, it will alert him using the {@link Notification} system three seconds after
     * the connection if the plugin is outdated.
     * 
     * @param event Instance of {@link PlayerJoinEvent}.
     */
	
	public void onJoin(PlayerJoinEvent event) throws UnknownHostException, IOException {
		
		if(PermissionUtil.hasPermission(event.getPlayer(), "movietheatrecore.permission.admin", "mediaplayer.permission.admin")) {
			if(updater.checkForUpdates(false).isOutdated()) {
				Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
					event.getPlayer().sendMessage(configuration.plugin_outdated());
					new Notification(NotificationType.PLUGIN_OUTDATED, true).send(new Group("movietheatrecore.permission.admin"), new String[] { "" }, true);     
				}, 60L);
			}
		}
	}
}
