package com._650a.movietheatrecore.interfaces.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.interfaces.Interfaces;
import com._650a.movietheatrecore.items.ItemStacks;
import com._650a.movietheatrecore.video.Video;
import com._650a.movietheatrecore.util.PermissionUtil;

/** 
* The InventoryClickVideos class implements {@link Listener}, it consist
* of a single event method {@link #onClick(InventoryClickEvent)}.
*
* @author  hwic
* @version 1.0.0
* @since   2021-08-23 
*/

public class InventoryClickVideos implements Listener {
	
	private final Main plugin = Main.getPlugin(Main.class);
	
	private final Configuration configuration = new Configuration();
	
	private final Interfaces interfaces = new Interfaces();
	private final ItemStacks items = new ItemStacks();
	
    /**
     * Called whenever a {@link Player} clicks in an inventory, specially in
     * videos panel, see {@link Interfaces#getVideos()}, see also Bukkit documentation
     * : {@link InventoryClickEvent}. This is used to listen for selected video to be
     * opened in a video panel, see {@link Interfaces#getVideoPanel(Video)}.
     * 
     * @param event Instance of {@link InventoryClickEvent}.
     */
	
	@EventHandler
	public void onClick(InventoryClickEvent event) {
		
		if(event.getView().getTitle().contains(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Videos")) {
			
			if(!PermissionUtil.hasPermission(event.getWhoClicked(), "movietheatrecore.permission.admin")) {
				event.getWhoClicked().closeInventory();
				return;
			}
			
			event.setCancelled(true);
						
	        if(event.getSlotType() == InventoryType.SlotType.OUTSIDE) return;
	        if(event.getCurrentItem() == null) return;
	        if(event.getCurrentItem().getType() == Material.AIR) return;
	        if(!event.getCurrentItem().hasItemMeta()) return;
	        if(!event.getCurrentItem().getItemMeta().hasDisplayName()) return;
	        if(event.getSlot() > 54) return;
	        
	        if(event.getCurrentItem().getItemMeta().getDisplayName().equals(items.previous().getItemMeta().getDisplayName())) {
	        	
	        	if(plugin.getVideosPages().get(event.getWhoClicked().getUniqueId())-1 < 0) {
	        		event.getWhoClicked().sendMessage(configuration.no_page_left());
	        		return;
	        	}
	        	
	        	plugin.getVideosPages().replace(event.getWhoClicked().getUniqueId(), plugin.getVideosPages().get(event.getWhoClicked().getUniqueId())-1);
		        event.getWhoClicked().closeInventory();
				event.getWhoClicked().openInventory(interfaces.getVideos(plugin.getVideosPages().get(event.getWhoClicked().getUniqueId())));
				return;
	        }
	        
	        if(event.getCurrentItem().getItemMeta().getDisplayName().equals(items.refresh().getItemMeta().getDisplayName())) {      	
		        event.getWhoClicked().closeInventory();
				event.getWhoClicked().openInventory(interfaces.getVideos(plugin.getVideosPages().get(event.getWhoClicked().getUniqueId())));
				return;
	        }
	        
	        if(event.getCurrentItem().getItemMeta().getDisplayName().equals(items.next().getItemMeta().getDisplayName())) {
	        	
	        	if(plugin.getVideosPages().get(event.getWhoClicked().getUniqueId())+1 > (int) (plugin.getRegisteredVideos().size() / 45)) {
	        		event.getWhoClicked().sendMessage(configuration.no_page_left());
	        		return;
	        	}
	        	
	        	plugin.getVideosPages().replace(event.getWhoClicked().getUniqueId(), plugin.getVideosPages().get(event.getWhoClicked().getUniqueId())+1);
		        event.getWhoClicked().closeInventory();
				event.getWhoClicked().openInventory(interfaces.getVideos(plugin.getVideosPages().get(event.getWhoClicked().getUniqueId())));
				return;
	        }
	        
	        if(event.getCurrentItem().getType() == Material.ITEM_FRAME) {
	        	
		        event.getWhoClicked().closeInventory();        
		        plugin.getVideoPanels().put(event.getWhoClicked().getUniqueId(), plugin.getRegisteredVideos().get(event.getSlot() +
		        		plugin.getVideosPages().get(event.getWhoClicked().getUniqueId())*45));
		        		        
		        event.getWhoClicked().openInventory(interfaces.getVideoPanel(plugin.getRegisteredVideos().get(event.getSlot() +
		        		plugin.getVideosPages().get(event.getWhoClicked().getUniqueId())*45)));
		        
	        	plugin.getVideosPages().replace(event.getWhoClicked().getUniqueId(), 0);
	        }
		}
	}
}
