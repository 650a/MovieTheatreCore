package com._650a.movietheatrecore.tasks;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.scheduler.BukkitRunnable;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.group.Group;
import com._650a.movietheatrecore.notification.Notification;
import com._650a.movietheatrecore.notification.NotificationType;
import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.util.Format;
import com._650a.movietheatrecore.video.Video;
import com._650a.movietheatrecore.video.data.VideoData;
import com._650a.movietheatrecore.video.instance.VideoInstance;

/** 
* The TaskAsyncLoadConfigurations class extends {@link BukkitRunnable} have no special
* constructors, have an method, see {@link #run()}. As named, the task is used to load video
* configurations. The task is runned for example on {@link Main#onEnable()}. The task is runned
* asynchronously from the main thread for I/O opperations, this is a thread-safe task since the
* video isn't parametered to run on start and it isn't accessing sensible {@link Bukkit} API content.
*
* @author  hwic
* @version 1.0.0
* @since   2021-08-23 
*/

public class TaskAsyncLoadConfigurations extends BukkitRunnable {
	
	private final Main plugin = Main.getPlugin(Main.class);
	private final Configuration configuration = new Configuration();
	
	/**
	* Runs a task that will load and register videos contained in {@link Configuration#getVideosFolder()}
	* to {@link Main#getRegisteredVideos()}.
	*/
	
	@Override
	public void run() {
		
		plugin.getTasks().add(getTaskId());
		
		File[] files = configuration.getVideosFolder().listFiles();
		
		plugin.getRegisteredVideos().clear();
		
		for(File file : files) {
			if(!file.isDirectory()) {
				
				File videoConfiguration = new File(configuration.getVideosFolder() + "/" + FilenameUtils.removeExtension(file.getName()), 
						FilenameUtils.removeExtension(file.getName()) + ".yml");
				
				if(Format.getCompatibleFormats().contains(FilenameUtils.getExtension(file.getName()))) {
					if(!videoConfiguration.exists()) {
						Video video = new Video(videoConfiguration);
						try {
							video.createConfiguration(file);
							Bukkit.getLogger().info("[MovieTheatreCore]: Generated metadata for " + file.getName() + ".");
						}catch (IOException | InvalidConfigurationException e) {
							Bukkit.getLogger().warning("[MovieTheatreCore]: Failed to generate metadata for " + file.getName() + ": " + e.getMessage());
						}
						if(!videoConfiguration.exists()) {
							continue;
						}
					}

					Video video = new Video(videoConfiguration);
					
					if(video.isLoaded()) {
						
						VideoData videoData = video.getVideoData();
						
						Bukkit.getScheduler().runTask(plugin, new Runnable() {
							@Override
							public void run() {
								videoData.loadThumbnail();
							}
						});
													
						if(videoData.getRunOnStartup()) {
							for(VideoInstance videoInstance : video.getInstances()) {
								
								Screen screen = videoInstance.getScreen();
								screen.setRunning(true);
								screen.display();
							}
						}
					}else {
						try {
							video.setLoaded(false);
						}catch (IOException | InvalidConfigurationException e) {
							e.printStackTrace();
						}
					}
					plugin.getRegisteredVideos().add(video);
				}
			}
		}
	    Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.DARK_GRAY + "[MovieTheatreCore]: " + ChatColor.GRAY + "Videos successfully registered. (" + plugin.getRegisteredVideos().size() + ")");
        new Notification(NotificationType.VIDEOS_RELOADED, true).send(new Group("movietheatrecore.permission.admin"), new String[] { "" }, true);       
	}
}
