package com._650a.movietheatrecore.ffmpeg;

import java.io.File;
import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.dependency.DependencyManager;
import com._650a.movietheatrecore.system.SystemType;

/** 
* The Ffmpeg class, is used in order to use the ffmpeg library
* while the plugin is running, and this on all operating system,
* see {@link System} and {@link SystemType}.
*
* @author  hwic
* @version 1.0.0
* @since   2022-07-03 
*/

public class Ffmpeg {
	
	private final Main plugin = Main.getPlugin(Main.class);
	
    /**
     * Gets the ffmpeg library file according to used operating system.
     *
     * @return The ffmpeg library file.
     */
	
	public File getLibraryFile() {
		return new File(plugin.getDataFolder() + "/runtime/bin/", getBinaryName());
	}
	
    /**
     * Gets whether the ffmpeg library file is installed.
     *
     * @return Whether the ffmpeg library file is installed.
     */
	
	public boolean isInstalled() {
		DependencyManager.ResolvedBinary resolved = plugin.getDependencyManager().resolveBinary(DependencyManager.BinaryType.FFMPEG, false);
		return resolved != null && resolved.isValid();
	}

	public String getExecutablePath() {
		return plugin.getDependencyManager().getExecutablePath(DependencyManager.BinaryType.FFMPEG);
	}

	public File getExecutableFile() {
		return plugin.getDependencyManager().getExecutableFile(DependencyManager.BinaryType.FFMPEG);
	}

	public boolean isAvailable() {
		return plugin.getDependencyManager().isAvailable(DependencyManager.BinaryType.FFMPEG);
	}
	
    /**
     * Gets the ffmpeg library file lenght according to used operating system.
     *
     * @return The ffmpeg library file lenght.
     */
	
	public long getFileLength() {
		if(com._650a.movietheatrecore.system.System.getSystemType().equals(SystemType.LINUX)) {return 76000000;}
		if(com._650a.movietheatrecore.system.System.getSystemType().equals(SystemType.WINDOWS)) {return 112000000;}
		if(com._650a.movietheatrecore.system.System.getSystemType().equals(SystemType.MAC)) {return 76000000;}
		return 76000000;
	}
	
    /**
     * Trigger an ffmpeg download via the dependency manager.
     */
	
	@SuppressWarnings("deprecation")
	public void download() {
		plugin.getDependencyManager().resolveBinary(DependencyManager.BinaryType.FFMPEG, true);
	}

	private String getBinaryName() {
		if(com._650a.movietheatrecore.system.System.getSystemType().equals(SystemType.WINDOWS)) {
			return "ffmpeg.exe";
		}
		return "ffmpeg";
	}
}
