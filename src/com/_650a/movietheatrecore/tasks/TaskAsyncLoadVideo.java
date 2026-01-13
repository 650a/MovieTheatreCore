package com._650a.movietheatrecore.tasks;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.FileOutputStream;
import java.util.zip.ZipOutputStream;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.scheduler.BukkitRunnable;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.group.Group;
import com._650a.movietheatrecore.image.renderer.ImageRenderer;
import com._650a.movietheatrecore.map.colors.MapColorPalette;
import com._650a.movietheatrecore.notification.Notification;
import com._650a.movietheatrecore.notification.NotificationType;
import com._650a.movietheatrecore.resourcepack.ResourcePack;
import com._650a.movietheatrecore.system.SystemType;
import com._650a.movietheatrecore.util.GIFUtil;
import com._650a.movietheatrecore.util.ImageUtil;
import com._650a.movietheatrecore.video.Video;
import com._650a.movietheatrecore.video.data.VideoData;
import com._650a.movietheatrecore.video.data.cache.Cache;
import com._650a.movietheatrecore.util.ProgressBar;

/** 
* The TaskAsyncLoadVideo class extends {@link BukkitRunnable} has a single constructor
* and method, see {@link #run()}. As named, the task is used to load video, see
* {@link Video#load()}. The task is runned asynchronously from the main thread for I/O
* opperations, this shouldn't cause problems to be runned asynchronously until video
* thumbnail creation but again its widely stable.
*
* @author  hwic
* @version 1.0.0
* @since   2021-08-23 
*/

public class TaskAsyncLoadVideo extends BukkitRunnable {
	
	private final Main plugin = Main.getPlugin(Main.class);
	private final Configuration configuration = new Configuration();
		
    private Video video;
    
	/**
	* Constructor for TaskAsyncLoadVideo class, creates an TaskAsyncLoadVideo variable
	* according to a {@link Video}.
	* 
	* @param video The video that is going to be load.
	*/
    
    public TaskAsyncLoadVideo(Video video) {
        this.video = video;
    }
    
	/**
	* Runs a task that will load the {@link Video} passed earlier in the constructor. Loading
	* a video can take time according to the video lenght and their options, see {@link Video#getSize()}.
	* 
	* The estimated time shown is a magnified average, it shouldn't be trusted anymore.
	*/
    
	@SuppressWarnings("deprecation")
	public void run() {
		
		try {
			plugin.getTasks().add(getTaskId());
			
	        long time = System.currentTimeMillis();
	                
	        //new Notification(NotificationType.VIDEO_PROCESSING_FRAMES_STARTING, true).send(new Group("movietheatrecore.permission.admin"), new String[] { video.getName() }, true);
	        //new Notification(NotificationType.VIDEO_PROCESSING_ESTIMATED_TIME, false).send(new Group("movietheatrecore.permission.admin"), new String[] { String.valueOf(Math.round((video.getVideoFile().length()*Math.pow(10, -6)))) }, true);
	        
	        String framesExtension = video.getFramesExtension();
	        int framesCount = video.getFramesFolder().listFiles().length;
	        if (configuration.debug_render()) {
	        	plugin.getLogger().info("[MovieTheatreCore]: Preparing frames for video " + video.getName() + " (" + framesCount + "/" + video.getTotalFrames() + ").");
	        }
	                
	        VideoData videoData = new VideoData(video); 
	        
	        if(framesCount < video.getTotalFrames()) {
	        	
	        	if(com._650a.movietheatrecore.system.System.getSystemType().equals(SystemType.LINUX) || com._650a.movietheatrecore.system.System.getSystemType().equals(SystemType.OTHER)) {
	        		if(configuration.plugin_force_permissions()) {
	                	try {
	        				File ffmpegFile = plugin.getFfmpeg().getExecutableFile();
	        				if(ffmpegFile != null) {
	        					Runtime.getRuntime().exec("chmod -R 777 " + FilenameUtils.separatorsToUnix(ffmpegFile.getAbsolutePath())).waitFor();
	        				}
	        			}catch (InterruptedException | IOException e) {
	        				e.printStackTrace();
	        			}
	        		}
	        	}
	        	
	    		String[] videoCommand = {FilenameUtils.separatorsToUnix(plugin.getFfmpeg().getExecutablePath())
	    				, "-hide_banner",
	    				"-loglevel", "error",
	    				"-i", FilenameUtils.separatorsToUnix(video.getVideoFile().getAbsolutePath()),
	    				"-start_number", String.valueOf(framesCount),
	    				"-q:v", "0",
	    				FilenameUtils.separatorsToUnix(new File(video.getFramesFolder().getPath(), "%d.jpg").getAbsolutePath())};
	            
	            ProcessBuilder videoProcessBuilder = new ProcessBuilder(videoCommand);
	            Bukkit.getLogger().info(Arrays.toString(videoCommand).replace(",", ""));
	                     
	            try {
	            	ProgressBar progressBar = new ProgressBar(framesCount, video.getTotalFrames(), video.getName(),
	            			'▉', net.md_5.bungee.api.ChatColor.RED, net.md_5.bungee.api.ChatColor.GREEN);
	            	
	    			Process process = videoProcessBuilder.inheritIO().start();
	    			plugin.getProcess().add(process);
	    			
	    			Group group = new Group("movietheatrecore.permission.admin");
	    			
	    			int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
						@Override
						public void run() {
							try (Stream<Path> files = Files.list(video.getFramesFolder().toPath())) {
							    progressBar.setProgress((int)files.count());
							    progressBar.send(group, progressBar.build(), net.md_5.bungee.api.ChatColor.GRAY + " (1/3)");
							}catch (IOException e) {
								e.printStackTrace();
							}
						}
	    			 }, 0L, 0L);
	    			process.waitFor();
	    			Bukkit.getScheduler().cancelTask(task);
	    		}catch (IOException | InterruptedException e) {
	    			e.printStackTrace();
	    		}
	            
	            if(configuration.verify_files_on_load()) {
	            	
	            	int count = 0;
	            	int total = video.getTotalFrames();
	            	
	                while(count < total) {
	                	
	                	File previous = new File(video.getFramesFolder(), String.valueOf(count-1) + "." + video.getFramesExtension());
	                	File next = new File(video.getFramesFolder(), count + "." + video.getFramesExtension());
	                	
	                	if(!next.exists() && previous.exists()) {
	    					try {
	    						ImageIO.write(ImageIO.read(previous), video.getFramesExtension().replace(".", ""), next);
	    					}catch (IOException e) {
	    						e.printStackTrace();
	    					}
	                	}
	                	count++;
	                }
	            }
	        }
	        
	        new Notification(NotificationType.VIDEO_PROCESSING_FRAMES_FINISHED, true).send(new Group("movietheatrecore.permission.admin"), new String[] { video.getName() }, true);        
	        //new Notification(NotificationType.VIDEO_PROCESSING_AUDIO_STARTING, false).send(new Group("movietheatrecore.permission.admin"), new String[] { video.getName() }, true);
	        
	        if(!video.getFormat().equalsIgnoreCase("m3u8")) {
	            if(!video.getFormat().equalsIgnoreCase("gif")) {
	            	if(video.hasAudio()) {
	            		try {
	            			if(video.getAudioFolder().exists()) {
	            				FileUtils.cleanDirectory(video.getAudioFolder());
	            			}
	            		}catch (IOException e) {
	            			e.printStackTrace();
	            		}
	            		File initialAudio = new File(video.getAudioFolder(), "0.ogg");
	            		File fixedAudio = new File(video.getAudioFolder(), "0-fixed.ogg");
	            		String[] audioCommand = {FilenameUtils.separatorsToUnix(plugin.getFfmpeg().getExecutablePath()),
	            				"-hide_banner",
	            				"-loglevel", "error",
	            				"-i", FilenameUtils.separatorsToUnix(video.getVideoFile().getAbsolutePath()),
	            				"-ac", "2",
	            				"-ar", "48000",
	            				"-c:a", "libvorbis",
	            				"-f", "ogg",
	            				"-vn", FilenameUtils.separatorsToUnix(initialAudio.getAbsolutePath())};
	                	
	                    ProcessBuilder audioProcessBuilder = new ProcessBuilder(audioCommand);
	    	            Bukkit.getLogger().info(Arrays.toString(audioCommand).replace(",", ""));
	                    
	                    try {
	                    	Process process = audioProcessBuilder.inheritIO().start();
			    			plugin.getProcess().add(process);
	            			process.waitFor();
	            		}catch (IOException | InterruptedException e) {
	            			e.printStackTrace();
	            		}

	            		if(initialAudio.exists()) {
	            			String[] fixCommand = {FilenameUtils.separatorsToUnix(plugin.getFfmpeg().getExecutablePath()),
	            				"-hide_banner",
	            				"-loglevel", "error",
	            				"-y",
	            				"-i", FilenameUtils.separatorsToUnix(initialAudio.getAbsolutePath()),
	            				"-ac", "2",
	            				"-ar", "48000",
	            				"-c:a", "libvorbis",
	            				FilenameUtils.separatorsToUnix(fixedAudio.getAbsolutePath())};

	            			ProcessBuilder fixProcessBuilder = new ProcessBuilder(fixCommand);
	            			Bukkit.getLogger().info(Arrays.toString(fixCommand).replace(",", ""));

	            			try {
	            				Process fixProcess = fixProcessBuilder.inheritIO().start();
	            				plugin.getProcess().add(fixProcess);
	            				fixProcess.waitFor();
	            			}catch (IOException | InterruptedException e) {
	            				e.printStackTrace();
	            			}

	            			if(fixedAudio.exists()) {
	            				if(!initialAudio.delete()) {
	            					Bukkit.getLogger().warning("[MovieTheatreCore]: Failed to remove initial audio file before replacing.");
	            				}
	            				if(!fixedAudio.renameTo(initialAudio)) {
	            					Bukkit.getLogger().warning("[MovieTheatreCore]: Failed to replace audio file with fixed version.");
	            				}
	            			}
	            		}
	                    new ResourcePack().create(video);
	            	}
	            }else {
	            	try {
	    				GIFUtil.split(video.getVideoFile(), video.getFramesFolder());
	    			}catch (IOException e) {
	    				e.printStackTrace();
	    			}
	            }
	        }
	        if (configuration.debug_render()) {
	        	int extracted = video.getFramesFolder().listFiles().length;
	        	plugin.getLogger().info("[MovieTheatreCore]: Extracted " + extracted + " frames for video " + video.getName() + ".");
	        }
	        
	        new Notification(NotificationType.VIDEO_PROCESSING_AUDIO_FINISHED, true).send(new Group("movietheatrecore.permission.admin"), new String[] { video.getName() }, true);
	        //new Notification(NotificationType.VIDEO_PROCESSING_MINECRAFT_STARTING, false).send(new Group("movietheatrecore.permission.admin"), new String[] { video.getName() }, true);
			                
	        try {
	        	if(!videoData.getThumbnail().exists()) {
	        		
	    	        videoData.createThumbnail();		        	        
	        		
	        		Bukkit.getScheduler().runTask(plugin, new Runnable() {
						@Override
						public void run() {
			    			try {
				    			videoData.createMaps();
							}catch (IOException e) {
								e.printStackTrace();
							}
						}
	        		});
	    			ImageRenderer imageRenderer = new ImageRenderer(ImageIO.read(video.getVideoData().getThumbnail()));
	    			imageRenderer.calculateDimensions();
					
					video.setMinecraftWidth(imageRenderer.columns);
	    			video.setMinecraftHeight(imageRenderer.lines);
	        	}
	        	
	        	if(configuration.detect_duplicated_frames()) {
	    			if(!new File(video.getFramesFolder(), "duplicated.txt").exists()) {
	    				
	    				new File(video.getFramesFolder(), "duplicated.txt").createNewFile();
	    				
	    			    FileWriter fileWriter = new FileWriter(video.getFramesFolder().getPath() + "/duplicated.txt", true);
	    			    
	    			    final double max = configuration.ressemblance_to_skip();
	    			    final int total = video.getTotalFrames()-1;
	    			    
	    			    for(int i = 0; i < total; i++) {
	    			    	
	    			    	BufferedImage original = ImageIO.read(new File(video.getFramesFolder(), i + framesExtension));
	    			    	BufferedImage next = ImageIO.read(new File(video.getFramesFolder(), (i+1) + framesExtension));
	    			    			    			    	
	    			    	if(ImageUtil.getResemblance(original, next) > max) {
	    			    		fileWriter.write((i+1) + "\n");
	    			    	}
	    			    }
	    			    fileWriter.close();	
	    			}
	        	} 
			}catch (IOException | InvalidConfigurationException e) {
				e.printStackTrace();
			}
	                
	        if(!videoData.getRealTimeRendering()) {
	        	
	        	int total = video.getTotalFrames();
	        	int count = videoData.getCacheFolder().listFiles().length;
	        	
	        	ImageRenderer imageRenderer;
	        	
				Group group = new Group("movietheatrecore.permission.admin");
	        	
	        	while(count < total) {
	    			try {
	    				
	    				File frame = new File(video.getFramesFolder(), count + framesExtension);
	    				
	    				if(frame.exists()) {
	    					
		    				imageRenderer = new ImageRenderer(ImageIO.read(frame));
		    	    		imageRenderer.calculateDimensions();
		    	    		imageRenderer.splitImages();
		    				
		    	    		if(!video.isCacheCompressed()) {
		    	    			
		        				File file = new File(videoData.getCacheFolder(), String.valueOf(count));
		        				file.mkdir();
		        	    		
		        				for(int j = 0; j < imageRenderer.getBufferedImages().length; j++) {
		        					new Cache(new File(file, String.valueOf(j) + ".cache")).createCache(imageRenderer.getBufferedImages()[j]);
		        				}
		    	    		}else {
		    	    			
		    	    			FileOutputStream fout = new FileOutputStream(video.getVideoData().getCacheFolder() + "/" + String.valueOf(count) + ".zip");
		    	    			ZipOutputStream zout = new ZipOutputStream(fout);
		    	    			
		    	    			for(int j = 0; j < imageRenderer.getBufferedImages().length; j++) {
		    	    				
		    	    			    ZipEntry ze = new ZipEntry(String.valueOf(j) + ".cache");
		    	    			    zout.putNextEntry(ze);
		    	    			    zout.write(MapColorPalette.convertImage(imageRenderer.getBufferedImages()[j]));
		    	    			    zout.closeEntry();
		    	    			}
		    	    			zout.close();
		    	    			fout.close();
		    	    		}
		    	    		
		                	ProgressBar progressBar = new ProgressBar(count, total, video.getName(),
		                			'▉', net.md_5.bungee.api.ChatColor.RED, net.md_5.bungee.api.ChatColor.GREEN);
		    	    		
		                	progressBar.setProgress(count);
		                	progressBar.send(group, progressBar.build(), net.md_5.bungee.api.ChatColor.GRAY + "(3/3)");
	    				}
	                	
	    				count++;
	    			}catch (IOException | InvalidConfigurationException e) {
	    				e.printStackTrace();
	    			}
	        	}
	        	
	        	if(configuration.verify_files_on_load()) {
	        		
	            	count = 0;
	            	
	            	while(count < total) {
	        			try {
	        				
	        				File previous = new File(videoData.getCacheFolder(), String.valueOf(count-1));
	        				File next = new File(videoData.getCacheFolder(), String.valueOf(count));
	        				
	        				if(!next.exists() && previous.exists()) {
	        					
	            				imageRenderer = new ImageRenderer(ImageIO.read(new File(video.getFramesFolder(), String.valueOf(count-1) + framesExtension)));
	            	    		imageRenderer.calculateDimensions();
	            	    		imageRenderer.splitImages();
	        					
	            				for(int j = 0; j < imageRenderer.getBufferedImages().length; j++) {
	            					File cache = new File(next, String.valueOf(j) + ".cache");
	            					if(cache.exists()) new Cache(cache).createCache(imageRenderer.getBufferedImages()[j]);
	            				}        	    		
	        				}
	        				count++;
	        			}catch (IOException | InvalidConfigurationException e) {
	        				e.printStackTrace();
	        			}
	            	}
	        	}
	    		
	        	try {
					video.setLoaded(true);
				}catch (IOException | InvalidConfigurationException e) {
					e.printStackTrace();
				}
	        	
	        	if(!videoData.getRealTimeRendering()) {
	                if(configuration.frames_delete_on_loaded()) {
	                    Bukkit.getLogger().warning("[MovieTheatreCore]: Frame deletion is disabled to preserve scaling quality across screens.");
	                }
	        	}
	        	
	            if(configuration.video_delete_on_loaded()) {
	            	video.getVideoFile().delete();
	            }
	        }
	        
	        //new Notification(NotificationType.VIDEO_PROCESSING_MINECRAFT_FINISHED, true).send(new Group("movietheatrecore.permission.admin"), new String[] { video.getName() }, true);
	        new Notification(NotificationType.VIDEO_PROCESSING_FINISHED, false).send(new Group("movietheatrecore.permission.admin"), new String[] { video.getName(), String.valueOf(Math.round(((System.currentTimeMillis() - time) / 1000)/60)) }, true);
			
	        plugin.getLoadingVideos().remove(video.getName());
	        
		    Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.DARK_GRAY + "[MovieTheatreCore]: " + ChatColor.GRAY + video.getName() + " successfully loaded.");
		}catch (Exception e) {
			run();
		}
    }
}
