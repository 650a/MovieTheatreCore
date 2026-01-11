package fr.xxathyx.mediaplayer.actionbar;

import fr.xxathyx.mediaplayer.util.ActionBar;

/** 
* The ActionBarVersion class is only called once, while the plugin
* is loading, see {@link Main#onEnable()}. It is based on a
* single method {@link #getActionBar}, that return an adequate
* instance of {@link ActionBar} according to the server running
* version, the variable is next used in the {@link Main} class.
*
* @author  Xxathyx
* @version 1.0.0
* @since   2021-08-23 
*/

public class ActionBarVersion {
	
    /**
     * Gets {@link ActionBar} variable according to the running server version.
     * 
     * <p> <strong>Note: </strong> Does return null if the server running version is unreconized or isn't supported.
     * 
     * @return ActionBar of the server version.
     */
	
	public ActionBar getActionBar() {
		return new ReflectiveActionBar();
	}
}
