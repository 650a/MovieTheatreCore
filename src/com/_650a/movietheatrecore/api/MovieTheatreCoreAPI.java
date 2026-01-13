package com._650a.movietheatrecore.api;

import com._650a.movietheatrecore.Main;

/** 
* The MovieTheatreCoreAPI class, is used as a pass-throught to use the
* plugin and its functionalities, as it is build in api style.
*
* @author  hwic
* @version 1.0.0
* @since   2022-07-03 
*/

public class MovieTheatreCoreAPI {
	
    /**
     * Gets MovieTheatreCores main class, which grant access to all information about the plugin.
     *
     * @return MovieTheatreCore main class.
     */
	
	public static Main getPlugin() {
		return Main.getPlugin(Main.class);
	}
}