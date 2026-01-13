package com._650a.movietheatrecore.ffmpeg;

import java.io.File;
import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.dependency.DependencyManager;
import com._650a.movietheatrecore.system.SystemType;

/** 
* The Ffprobe class, is used in order to use the ffprobe library
* while the plugin is running, and this on all operating system,
* see {@link System} and {@link SystemType}.
*
* @author  hwic
* @version 1.0.0
* @since   2022-07-03 
*/

public class Ffprobe {
	
	private final Main plugin = Main.getPlugin(Main.class);
	
    /**
     * Gets the ffprobe library file according to used operating system.
     *
     * @return The ffprobe library file.
     */
	
	public File getLibraryFile() {
		return new File(plugin.getDataFolder() + "/runtime/bin/", getBinaryName());
	}
	
    /**
     * Gets whether the ffprobe library file is installed.
     *
     * @return Whether the ffprobe library file is installed.
     */
	
	public boolean isInstalled() {
		DependencyManager.ResolvedBinary resolved = plugin.getDependencyManager().resolveBinary(DependencyManager.BinaryType.FFPROBE, false);
		return resolved != null && resolved.isValid();
	}

	public String getExecutablePath() {
		return plugin.getDependencyManager().getExecutablePath(DependencyManager.BinaryType.FFPROBE);
	}

	public File getExecutableFile() {
		return plugin.getDependencyManager().getExecutableFile(DependencyManager.BinaryType.FFPROBE);
	}

	public boolean isAvailable() {
		return plugin.getDependencyManager().isAvailable(DependencyManager.BinaryType.FFPROBE);
	}
	
    /**
     * Gets the ffprobe library file lenght according to used operating system.
     *
     * @return The ffprobe library file lenght.
     */
	
	public long getFileLength() {
		if(com._650a.movietheatrecore.system.System.getSystemType().equals(SystemType.LINUX)) {return 76000000;}
		if(com._650a.movietheatrecore.system.System.getSystemType().equals(SystemType.WINDOWS)) {return 112000000;}
		if(com._650a.movietheatrecore.system.System.getSystemType().equals(SystemType.MAC)) {return 76000000;}
		return 76000000;
	}
	
    /**
     * Trigger an ffprobe download via the dependency manager.
     */
	
	@SuppressWarnings("deprecation")
	public void download() {
		plugin.getDependencyManager().resolveBinary(DependencyManager.BinaryType.FFPROBE, true);
	}

	private String getBinaryName() {
		if(com._650a.movietheatrecore.system.System.getSystemType().equals(SystemType.WINDOWS)) {
			return "ffprobe.exe";
		}
		return "ffprobe";
	}
}
