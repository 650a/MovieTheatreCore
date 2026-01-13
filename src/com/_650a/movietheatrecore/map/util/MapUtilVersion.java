package com._650a.movietheatrecore.map.util;

import org.bukkit.Bukkit;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.util.MapUtil;

/** 
* The MapUtilVersion is only called once, while the plugin
* is loading, see {@link Main#onEnable()}. It is based on a
* single method {@link #getMapUtil}, that return an adequate instance
* of {@link MapUtil} according to the server running version, the
* variable is next used in the {@link .Main} class.
*
* @author  hwic
* @version 1.0.0
* @since   2021-08-23 
*/

public class MapUtilVersion {
	
    /**
     * Gets {@link MapUtil} variable according to the running server version.
     * 
     * <p> <strong>Note: </strong> Does return null if the server running version is sunreconized or isn't supported.
     * 
     * @return MapUtil of the server version.
     */
	
	private final Main plugin = Main.getPlugin(Main.class);
	
	public MapUtil getMapUtil() {
		if(plugin.getServerVersion().equals("v1_21_R7")) {
			System.out.print("MovieTheatreCore is running on the latest supported minecraft version : "
					+ Bukkit.getServer().getClass().getPackage().getName() + "\n");
		}
		return new ReflectiveMapUtil();
	}
}
