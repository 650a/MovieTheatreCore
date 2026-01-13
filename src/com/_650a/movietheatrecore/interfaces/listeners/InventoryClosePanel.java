package com._650a.movietheatrecore.interfaces.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.interfaces.Interfaces;
import com._650a.movietheatrecore.sound.SoundPlayer;
import com._650a.movietheatrecore.sound.SoundType;
import com._650a.movietheatrecore.video.Video;

/** 
* The InventoryClosePanel class implements {@link Listener}, it consist
* of a single event method {@link #onClose(InventoryCloseEvent)}.
*
* @author  hwic
* @version 1.0.0
* @since   2021-08-23 
*/

public class InventoryClosePanel implements Listener {
	
	private final Main plugin = Main.getPlugin(Main.class);
	
    /**
     * Called whenever a {@link Player} close an open inventory, specially video and screen
     * panels, see {@link Interfaces#getVideoPanel(Video)} or {@link Interfaces#getScreenPanel(Video)},
     * see also Bukkit documentation : {@link InventoryCloseEvent}. This is used to know if
     * a player is still in a video panel, remove him when he closes the inventory.
     * 
     * @param event Instance of {@link InventoryCloseEvent}.
     */
	
	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if(plugin.getVideoPanels().containsKey(event.getPlayer().getUniqueId())) {
			plugin.getVideoPanels().remove(event.getPlayer().getUniqueId());
			SoundPlayer.playSound((Player) event.getPlayer(), SoundType.NOTIFICATION_DOWN);
		}
		if(plugin.getScreenPanels().containsKey(event.getPlayer().getUniqueId())) {
			plugin.getScreenPanels().remove(event.getPlayer().getUniqueId());
			SoundPlayer.playSound((Player) event.getPlayer(), SoundType.NOTIFICATION_DOWN);
		}
		if(plugin.getContentsPanels().containsKey(event.getPlayer().getUniqueId())) {
			plugin.getContentsPanels().remove(event.getPlayer().getUniqueId());
			SoundPlayer.playSound((Player) event.getPlayer(), SoundType.NOTIFICATION_DOWN);
		}
	}
}