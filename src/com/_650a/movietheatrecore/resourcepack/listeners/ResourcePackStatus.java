package com._650a.movietheatrecore.resourcepack.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;

import com._650a.movietheatrecore.Main;

/** 
* The ResourcePackStatus class implements {@link Listener}, it consist
* of a single event method {@link #onResourcepackStatusEvent(PlayerResourcePackStatusEvent)}.
*
* @author  hwic
* @version 1.0.0
* @since   2022-07-16 
*/

public class ResourcePackStatus implements Listener {
	
	private final Main plugin = Main.getPlugin(Main.class);
	
    /**
     * Called whenever a {@link Player} update is ressource-pack application.
     * see Bukkit documentation : {@link PlayerResourcePackStatusEvent}. This
     * is used to know if a play successully downloaded a ressource-pack.
     *
     * @param event Instance of {@link PlayerResourcePackStatusEvent}.
     */
	
    @EventHandler
    public void onResourcepackStatusEvent(PlayerResourcePackStatusEvent event) {
    	Status status = event.getStatus();
    	if(status.equals(Status.SUCCESSFULLY_LOADED)) {
    		plugin.getPlayersScreens().put(event.getPlayer().getUniqueId(), null);
    	}
		plugin.getPlaybackManager().handleResourcePackStatus(event.getPlayer(), status);
    }
}
