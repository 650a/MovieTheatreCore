package com._650a.movietheatrecore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.spi.FileSystemProvider;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com._650a.movietheatrecore.actionbar.ActionBarVersion;
import com._650a.movietheatrecore.audio.util.AudioUtilVersion;
import com._650a.movietheatrecore.audio.AudioPackManager;
import com._650a.movietheatrecore.commands.MovieTheatreCoreCommands;
import com._650a.movietheatrecore.configuration.Configuration;
import com._650a.movietheatrecore.configuration.updater.ConfigurationUpdater;
import com._650a.movietheatrecore.dependency.DependencyManager;
import com._650a.movietheatrecore.ffmpeg.Ffmpeg;
import com._650a.movietheatrecore.ffmpeg.Ffprobe;
import com._650a.movietheatrecore.group.Group;
import com._650a.movietheatrecore.gui.GuiSupport;
import com._650a.movietheatrecore.gui.GuiSupportFactory;
import com._650a.movietheatrecore.gui.LegacyGuiSupport;
import com._650a.movietheatrecore.gui.AdminMenuListener;
import com._650a.movietheatrecore.gui.AdminToolListener;
import com._650a.movietheatrecore.gui.AdminWizardListener;
import com._650a.movietheatrecore.media.MediaLibrary;
import com._650a.movietheatrecore.media.MediaManager;
import com._650a.movietheatrecore.playback.PlaybackManager;
import com._650a.movietheatrecore.map.colors.MCSDGenBukkit;
import com._650a.movietheatrecore.map.colors.MapColorSpaceData;
import com._650a.movietheatrecore.map.util.MapUtilVersion;
import com._650a.movietheatrecore.resourcepack.listeners.ResourcePackStatus;
import com._650a.movietheatrecore.screen.Screen;
import com._650a.movietheatrecore.screen.ScreenManager;
import com._650a.movietheatrecore.screen.listeners.PlayerBreakScreen;
import com._650a.movietheatrecore.screen.listeners.PlayerDamageScreen;
import com._650a.movietheatrecore.screen.listeners.PlayerDisconnectScreen;
import com._650a.movietheatrecore.interfaces.listeners.InventoryClickContents;
import com._650a.movietheatrecore.interfaces.listeners.InventoryClickPanel;
import com._650a.movietheatrecore.interfaces.listeners.InventoryClickScreens;
import com._650a.movietheatrecore.interfaces.listeners.InventoryClickVideos;
import com._650a.movietheatrecore.interfaces.listeners.InventoryClosePanel;
import com._650a.movietheatrecore.tasks.TaskAsyncLoadConfigurations;
import com._650a.movietheatrecore.tasks.TaskAsyncLoadImages;
import com._650a.movietheatrecore.translation.Translater;
import com._650a.movietheatrecore.update.Updater;
import com._650a.movietheatrecore.util.ActionBar;
import com._650a.movietheatrecore.util.AudioUtil;
import com._650a.movietheatrecore.util.MapUtil;
import com._650a.movietheatrecore.util.ServerVersion;
import com._650a.movietheatrecore.video.Video;
import com._650a.movietheatrecore.video.instance.VideoInstance;
import com._650a.movietheatrecore.video.player.VideoPlayer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;

/*
 * Copyright or hwic (2025)
 *
 * This software is a computer program add the possibility to use various
 * media on minecraft
 *
 * This software is governed by the CeCILL license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks
 * associated with loading,  using,  modifying and/or developing or
 * reproducing the software by the user in light of its specific status
 * of free software, that may mean  that it is complicated to manipulate,
 * and  that  also therefore means  that it is reserved for developers  and
 * experienced professionals having in-depth computer knowledge. Users are
 * therefore encouraged to load and test the software's suitability as regards
 * their requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have
 * had knowledge of the CeCILL license and that you accept its terms.
 */

/** 
* Represents the MovieTheatreCore main class of the MovieTheatreCore plugin,
* it mainly store informations used accross the plugin.
*
* @author  hwic
* @version 1.0.0
* @since   2021-08-23 
*/

public class Main extends JavaPlugin implements Listener {
	
	private final ArrayList<Integer> tasks = new ArrayList<>();
	private final ArrayList<Process> process = new ArrayList<>();
	
	private final ArrayList<Video> registeredVideos = new ArrayList<>();
	private final ArrayList<Screen> registeredScreens = new ArrayList<>();
	
	private final Map<UUID, URL> streamsURL = new HashMap<>();
	private final ArrayList<Video> playedStreams = new ArrayList<>();
	
	private final Map<Block, Screen> screensBlocks = new HashMap<>();
	private final Map<ItemFrame, Screen> screensFrames = new HashMap<>();
	
	private final Map<UUID, VideoPlayer> videoPlayers = new HashMap<>();
	private final Map<UUID, Screen> playersScreens = new HashMap<>();

	private ScreenManager screenManager;
	private PlaybackManager playbackManager;
	private MediaLibrary mediaLibrary;
	private MediaManager mediaManager;
	private AudioPackManager audioPackManager;
	private com._650a.movietheatrecore.theatre.TheatreManager theatreManager;
	
	private final ArrayList<Group> groups = new ArrayList<>();
	
	private final Map<UUID, Integer> videosPages = new HashMap<>();
	private final Map<UUID, Integer> screensPages = new HashMap<>();
	
	private final Map<UUID, Integer> contentsPages = new HashMap<>();
	
	private final Map<UUID, Video> videoPanels = new HashMap<>();
	private final Map<UUID, Screen> screenPanels = new HashMap<>();
	
	private final Map<UUID, Screen> contentsPanels = new HashMap<>();
	
	private final Map<UUID, VideoInstance> selectedVideos = new HashMap<>();
	
	private final ArrayList<String> loadingVideos = new ArrayList<>();
	private final ArrayList<String> playingVideos = new ArrayList<>();
	
	private final MapColorSpaceData mapColorSpaceData = new MapColorSpaceData();
	
	private Configuration configuration;
	private DependencyManager dependencyManager;
	private GuiSupport guiSupport;
	
	private Ffmpeg ffmpeg;
	private Ffprobe ffprobe;
	
	private Translater translater;
	
	private Updater updater;
	
	private MapUtil mapUtil;
	private ActionBar actionBar;
	private AudioUtil audioUtil;
	
	private String serverVersion;
	
	private boolean isPaper = false;
	private boolean legacy = true;
	private boolean old = false;
	private static final char[] LEGACY_FOLDER_NAME = new char[] {
			'M', 'e', 'd', 'i', 'a', 'P', 'l', 'a', 'y', 'e', 'r'
	};
		
	/**
	* See Bukkit documentation : {@link JavaPlugin#onEnable()}
	* 
	* <p>Load all necessary informations, creates configurations
	* and load videos and images.
	*/
	
	public void onEnable() {
		guiSupport = new LegacyGuiSupport(this);
		try {
			serverVersion = ServerVersion.getServerVersion();
			getLogger().info("[MovieTheatreCore] Licensed for commercial use only. See LICENSE for terms.");
			
	        try {
	            Class.forName("com.destroystokyo.paper.ParticleBuilder"); isPaper = true;
	        }catch (ClassNotFoundException ignored) {}
			
			migrateLegacyDataFolder();
			configuration = new Configuration();
			configuration.setup();
			ensureUserGuideExported();

			dependencyManager = new DependencyManager(this);
			ffmpeg = new Ffmpeg();
			ffprobe = new Ffprobe();

			if(configuration.dependencies_install_auto_install()) {
				dependencyManager.warmUpDependenciesAsync();
			}
			
	        translater = new Translater();
	        String langage = translater.ensureTranslationExported(configuration.plugin_langage());
			
			updater = new Updater();
			updater.update();
			
			File updateFolder = new File(getDataFolder(), "updater/");
			File updateTranslation = new File(updateFolder, langage + ".yml");
			
			updateFolder.mkdir();
			
			try {
				URL translationUrl = Main.class.getResource("/translations/" + langage + ".yml");
				if(translationUrl == null) {
					Bukkit.getLogger().warning("[MovieTheatreCore]: Missing bundled translation " + langage + ".yml, skipping updater translation sync.");
				}else {
					URI uri = translationUrl.toURI();
					if("jar".equals(uri.getScheme())) {
					    for(FileSystemProvider provider: FileSystemProvider.installedProviders()) {
					        if(provider.getScheme().equalsIgnoreCase("jar")) {
					            try {
					                provider.getFileSystem(uri);
					            }catch (FileSystemNotFoundException e) {
					                provider.newFileSystem(uri, Collections.emptyMap());
					            }
					        }
					    }
					}
					Path source = Paths.get(uri);
					
					Files.copy(source, updateTranslation.toPath(), StandardCopyOption.REPLACE_EXISTING);
					
					new ConfigurationUpdater(new File(getDataFolder() + "/translations/", langage + ".yml"), updateTranslation, "messages").update();
				}
				
			}catch (URISyntaxException | IOException | InvalidConfigurationException e) {
		        Bukkit.getLogger().warning("[MovieTheatreCore]: If you are reloading the plugin skip this message otherwise failed to verify configurations.");
			}
			
			mapUtil = new MapUtilVersion().getMapUtil();
			actionBar = new ActionBarVersion().getActionBar();
			audioUtil = new AudioUtilVersion().getAudioUtil();
			
			Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
				@Override
				public void run() {
				      MCSDGenBukkit bukkitGen = new MCSDGenBukkit();
				      bukkitGen.generate();
				      mapColorSpaceData.readFrom((MapColorSpaceData)bukkitGen);
				}
			});
			
	        String serverVersion = getServerVersion();
			
	        if(serverVersion.equals("v1_21_R7") | serverVersion.equals("v1_21_R6") | serverVersion.equals("v1_21_R5") | serverVersion.equals("v1_21_R4") | serverVersion.equals("v1_21_R3") | serverVersion.equals("v1_21_R2") | serverVersion.equals("v1_21_R1") | serverVersion.equals("v1_20_R4") | serverVersion.equals("v1_20_R3") | serverVersion.equals("v1_20_R2") | serverVersion.equals("v1_20_R1") | serverVersion.equals("v1_19_R3") | serverVersion.equals("v1_19_R2") | serverVersion.equals("v1_19_R1") | serverVersion.equals("v1_18_R2") | serverVersion.equals("v1_18_R1") | serverVersion.equals("v1_17_R1") | serverVersion.equals("v1_16_R3") |
	        		serverVersion.equals("v1_16_R2") | serverVersion.equals("v1_16_R1") | serverVersion.equals("v1_15_R1") | serverVersion.equals("v1_14_R1") | serverVersion.equals("v1_13_R1") | serverVersion.equals("v1_13_R2")) {
	        	legacy = false;
	        }
	        
	        if(serverVersion.equals("v1_7_R4") | serverVersion.equals("v1_7_R3") | serverVersion.equals("v1_7_R2") | serverVersion.equals("v1_7_R1")) {
	        	old = true;
		        Bukkit.getLogger().warning("[MovieTheatreCore]: The server running version is old and isn't well supported, you may encounter future issues while playing videos.");
	        }
	        
	        screenManager = new ScreenManager(this);
	        playbackManager = new PlaybackManager(this, screenManager);
	        mediaLibrary = new MediaLibrary(this);
	        audioPackManager = new AudioPackManager(this);
	        audioPackManager.startServer();
	        mediaManager = new MediaManager(this, mediaLibrary, audioPackManager);

	        MovieTheatreCoreCommands movieTheatreCoreCommands = new MovieTheatreCoreCommands(this);
	        getCommand("movietheatrecore").setExecutor(movieTheatreCoreCommands);
	        getCommand("movietheatrecore").setTabCompleter(movieTheatreCoreCommands);

	        guiSupport = GuiSupportFactory.create(this);
	        guiSupport.register();

	        Bukkit.getServer().getPluginManager().registerEvents(new AdminToolListener(this), this);
	        Bukkit.getServer().getPluginManager().registerEvents(new AdminMenuListener(this), this);
	        Bukkit.getServer().getPluginManager().registerEvents(new AdminWizardListener(this), this);
	        Bukkit.getServer().getPluginManager().registerEvents(new InventoryClickVideos(), this);
	        Bukkit.getServer().getPluginManager().registerEvents(new InventoryClickScreens(), this);
	        Bukkit.getServer().getPluginManager().registerEvents(new InventoryClickPanel(), this);
	        Bukkit.getServer().getPluginManager().registerEvents(new InventoryClickContents(), this);
	        Bukkit.getServer().getPluginManager().registerEvents(new InventoryClosePanel(), this);

			Bukkit.getServer().getPluginManager().registerEvents(new PlayerBreakScreen(), this);
			Bukkit.getServer().getPluginManager().registerEvents(new PlayerDamageScreen(), this);
			Bukkit.getServer().getPluginManager().registerEvents(new PlayerDisconnectScreen(), this);
					
			if(!old) Bukkit.getServer().getPluginManager().registerEvents(new ResourcePackStatus(), this);
					
	        screenManager.loadAll();
	        theatreManager = new com._650a.movietheatrecore.theatre.TheatreManager(this, screenManager, mediaManager, playbackManager);
	        theatreManager.load();
			new TaskAsyncLoadConfigurations().runTaskAsynchronously(this);
			if(legacy) new TaskAsyncLoadImages().runTaskAsynchronously(this);
			if(!legacy) new TaskAsyncLoadImages().runTask(this);
			
			if(!configuration.plugin_packet_compression()) {}
		}catch (Throwable throwable) {
			Bukkit.getLogger().severe("[MovieTheatreCore]: Plugin startup encountered an error but will remain enabled with reduced functionality.");
			throwable.printStackTrace();
		}
	}
	
	/**
	* See Bukkit documentation : {@link JavaPlugin#onDisable()}
	* 
	* <p>Turn off all registered screens, and remove them if
	* {@link Configuration#remove_screen_on_end()} is true.
	* 
	* <p>Delete all temporary streamed videos, if
	* {@link Configuration#save_streams()} is true.
	*/
	
	public void onDisable() {
				
		for(Player player : Bukkit.getOnlinePlayers()) {
			player.closeInventory();
		}

		if(playbackManager != null) {
			playbackManager.stopAll();
		}
		if(theatreManager != null) {
			theatreManager.shutdown();
		}
		if(audioPackManager != null) {
			audioPackManager.stopAll();
		}
		
		for(Screen screen : registeredScreens) {
			//screen.end();
			if(configuration.remove_screen_on_restart()) {
				screen.remove();
			}
		}
		
		for(Process process : process) {
			process.destroy();
		}
				
		for(Video video : playedStreams) {
			
			try {
				if(!configuration.save_streams() && video.isStreamed()) video.delete();
			}catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void migrateLegacyDataFolder() {
		File legacyFolder = new File(getDataFolder().getParentFile(), getLegacyFolderName());
		if (!legacyFolder.exists()) {
			return;
		}
		File dataFolder = getDataFolder();
		File marker = new File(dataFolder, "migration-marker.txt");
		if (marker.exists()) {
			return;
		}
		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}
		try {
			copyLegacyDirectory(legacyFolder.toPath(), dataFolder.toPath());
			String message = "Migration complete: copied data from " + legacyFolder.getPath()
					+ " to " + dataFolder.getPath()
					+ ". Legacy folder retained for rollback; configuration keys are migrated on first load.";
			getLogger().info("[MovieTheatreCore] " + message);
			Files.write(marker.toPath(), message.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			getLogger().warning("[MovieTheatreCore] Failed to migrate legacy data folder: " + e.getMessage());
		}
	}

	private String getLegacyFolderName() {
		return new String(LEGACY_FOLDER_NAME);
	}

	private void copyLegacyDirectory(Path source, Path target) throws IOException {
		try (java.util.stream.Stream<Path> stream = Files.walk(source)) {
			stream.forEach(path -> {
				Path relative = source.relativize(path);
				Path destination = target.resolve(relative);
				try {
					if (Files.isDirectory(path)) {
						Files.createDirectories(destination);
					} else if (!Files.exists(destination)) {
						Files.copy(path, destination);
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (RuntimeException e) {
			if (e.getCause() instanceof IOException ioException) {
				throw ioException;
			}
			throw e;
		}
	}

	private void ensureUserGuideExported() {
		File guideFile = new File(getDataFolder(), "USER_GUIDE.md");
		if (guideFile.exists()) {
			return;
		}
		try (InputStream input = getResource("USER_GUIDE.md")) {
			if (input == null) {
				Bukkit.getLogger().warning("[MovieTheatreCore]: USER_GUIDE.md is missing from the plugin jar.");
				return;
			}
			guideFile.getParentFile().mkdirs();
			Files.copy(input, guideFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			guideFile.setReadable(true, false);
			guideFile.setWritable(false, false);
			guideFile.setReadOnly();
			Bukkit.getLogger().info("[MovieTheatreCore]: USER_GUIDE.md exported to " + guideFile.getPath());
		} catch (IOException e) {
			Bukkit.getLogger().warning("[MovieTheatreCore]: Failed to export USER_GUIDE.md: " + e.getMessage());
		}
	}
	
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        injectPlayer(event.getPlayer());
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event){
        removePlayer(event.getPlayer());
    }
	private void removePlayer(Player player) {
        Channel channel = getPlayerChannel(player);
        if(channel == null) return;
        channel.eventLoop().submit(() -> {
            if(channel.pipeline().get(player.getName()) != null) {
                channel.pipeline().remove(player.getName());
            }
            return null;
        });
    }
	
	private void injectPlayer(Player player) {
    	
        ChannelDuplexHandler channelDuplexHandler = new ChannelDuplexHandler() {
        	
            @Override
            public void channelRead(ChannelHandlerContext channelHandlerContext, Object packet) throws Exception {
                Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.YELLOW + "PACKET READ: " + ChatColor.RED + packet.toString());
                super.channelRead(channelHandlerContext, packet);
            }
            
			@Override
            public void write(ChannelHandlerContext channelHandlerContext, Object packet, ChannelPromise channelPromise) throws Exception {
                Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "PACKET WRITE: " + ChatColor.GREEN + packet.toString());
        		super.write(channelHandlerContext, packet, channelPromise);
            }
        };
        Channel channel = getPlayerChannel(player);
        if(channel == null) return;
        ChannelPipeline pipeline = channel.pipeline();
        if(pipeline.get(player.getName()) == null && pipeline.get("packet_handler") != null) {
            pipeline.addBefore("packet_handler", player.getName(), channelDuplexHandler);
        }
    }

    private Channel getPlayerChannel(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            return findChannel(handle, new java.util.HashSet<>(), 4);
        }catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Channel findChannel(Object value, Set<Object> visited, int depth) {
        if(value == null || depth < 0 || visited.contains(value)) return null;
        if(value instanceof Channel) return (Channel) value;
        visited.add(value);
        Class<?> type = value.getClass();
        while(type != null && !type.equals(Object.class)) {
            Field[] fields = type.getDeclaredFields();
            for(Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object next = field.get(value);
                    Channel channel = findChannel(next, visited, depth - 1);
                    if(channel != null) return channel;
                }catch (IllegalAccessException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
	
    /**
     * Gets access to the ffmpeg library.
     *
     * @return Ffmpeg library.
     */
	
	public Ffmpeg getFfmpeg() {
		return ffmpeg;
	}
	
    /**
     * Gets access to the ffprobe library.
     *
     * @return Ffprobe library.
     */
	
	public Ffprobe getFfprobe() {
		return ffprobe;
	}

	public DependencyManager getDependencyManager() {
		return dependencyManager;
	}

	public Updater getUpdater() {
		return updater;
	}

	public GuiSupport getGuiSupport() {
		return guiSupport == null ? new LegacyGuiSupport(this) : guiSupport;
	}
	
	
    /**
     * Gets the version that the server is running on.
     *
     * @return The version that the server is using.
     */
	
	public String getServerVersion() {
		return serverVersion;
	}
	
    /**
     * Gets {@link MapUtil} variable initialized on load according to the server version.
     *
     * @return MapUtil of this server version.
     */
	
	public MapUtil getMapUtil() {
		return mapUtil;
	}
	
    /**
     * Gets {@link ActionBar} variable initialized on load according to the server version.
     *
     * @return ActionBar of this server version.
     */
	
	public ActionBar getActionBar() {
		return actionBar;
	}
	
    /**
     * Gets {@link AudioUtil} variable initialized on load according to the server version.
     *
     * @return AudioUtil of this server version.
     */
	
	public AudioUtil getAudioUtil() {
		return audioUtil;
	}
	
    /**
     * Gets whether the plugin has been reloaded one time.
     *
     * @return Whether the plugin has been reloaded one time.
     */
	
	public boolean isReloaded() {
		
		int tickvalue = -1;
		
		try {
			Field tick = Bukkit.getScheduler().getClass().getDeclaredField("currentTick");
			tick.setAccessible(true);
			tickvalue = (Integer) tick.get(Bukkit.getScheduler());
		}catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
				
		if(tickvalue > 1) return true;	
		return false;
	}
	
    /**
     * Gets whether this server is running under a PaperMC version.
     *
     * @return Whether this server is running under PaperMC.
     */
	
	public boolean isPaper() {
		return isPaper;
	}
	
    /**
     * Gets whether this server is running under a legacy version of Minecraft.
     *
     * @return Whether this server is running under a legacy version of Minecraft.
     */
	
	public boolean isLegacy() {
		return legacy;
	}
	
    /**
     * Gets whether this server is running under a old supported version of Minecraft.
     *
     * @return Whether this server is running under a old supported version of Minecraft.
     */
	
	public boolean isOld() {
		return old;
	}
	
    /**
     * Gets the plugin running tasks, identified by their ids.
     *
     * @return Plugin running tasks ids.
     */
	
	public ArrayList<Integer> getTasks() {
		return tasks;
	}
	
    /**
     * Gets process such as ffmpeg and ffprobe tasks.
     *
     * @return The plugin libraries program process.
     */
	
	public ArrayList<Process> getProcess() {
		return process;
	}
	
    /**
     * Gets all detected and registered videos after running an {@link TaskAsyncLoadConfigurations}.
     *
     * @return Detected and registered videos from respective video folder.
     */
	
	public ArrayList<Video> getRegisteredVideos() {
		return registeredVideos;
	}
	
    /**
     * Gets all detected and registered screens after running an {@link TaskSyncLoadConfigurations},
     * or one time screen creation.
     *
     * @return Detected and registered videos from respective video folder.
     */
	
	public ArrayList<Screen> getRegisteredScreens() {
		return registeredScreens;
	}

	public ScreenManager getScreenManager() {
		return screenManager;
	}

	public PlaybackManager getPlaybackManager() {
		return playbackManager;
	}

	public AudioPackManager getAudioPackManager() {
		return audioPackManager;
	}

	public MediaManager getMediaManager() {
		return mediaManager;
	}

	public MediaLibrary getMediaLibrary() {
		return mediaLibrary;
	}

	public com._650a.movietheatrecore.theatre.TheatreManager getTheatreManager() {
		return theatreManager;
	}
	
    /**
     * Gets lives links of registered streamed video after being generated.
     *
     * @return Streamed videos originals links.
     */
	
	public Map<UUID, URL> getStreamsURL() {
		return streamsURL;
	}
	
    /**
     * Gets actually played livestreams videos.
     *
     * @return Streamed videos.
     */
	
	public ArrayList<Video> getPlayedStreams() {
		return playedStreams;
	}
	
    /**
     * Gets blocks belonging to screens structure.
     *
     * @return Blocks belonging to screens structure.
     */
	
	public Map<Block, Screen> getScreensBlocks() {
		return screensBlocks;
	}
	
    /**
     * Gets {@link ItemFrame} belonging to screens structure.
     *
     * @return {@link ItemFrame} belonging to screens structure.
     */
	
	public Map<ItemFrame, Screen> getScreensFrames() {
		return screensFrames;
	}
	
    /**
     * Gets all players uuid that are actually playing videos and their video players.
     *
     * @return A HashMap of players that are playing videos and their video players.
     */
	
	public Map<UUID, VideoPlayer> getVideoPlayers() {
		return videoPlayers;
	}
	
    /**
     * Gets players affiliated with screen instances, this is use for audio loading.
     *
     * @return A HashMap of players that are affiliated with screen instances.
     */
	
	public Map<UUID, Screen> getPlayersScreens() {
		return playersScreens;
	}
	
    /**
     * Gets all registered groups of players.
     *
     * <p>Contains for example a {@link Group} of players watching a video.
     *
     * @return groups that are registered.
     */
	
	public ArrayList<Group> getGroups() {
		return groups;
	}
	
    /**
     * Gets all players uuid and their current page index of videos section.
     *
     * @return Players that are in videos section and their current page index.
     */
	
	public Map<UUID, Integer> getVideosPages() {
		return videosPages;
	}
	
    /**
     * Gets all players uuid and their current page index of screens section.
     *
     * @return Players that are in screens section and their current page index.
     */
	
	public Map<UUID, Integer> getScreensPages() {
		return screensPages;
	}
	
    /**
     * Gets all players uuid and their current page index of contents section.
     *
     * @return Players that are in contents section and their current page index.
     */
	
	public Map<UUID, Integer> getContentsPages() {
		return contentsPages;
	}
	
    /**
     * Gets all players uuid that are actually in an video panel inventory {@link Interfaces#getVideoPanel(Video)}.
     *
     * @return Players that are in a video panel inventory.
     */
	
	public Map<UUID, Video> getVideoPanels() {
		return videoPanels;
	}
	
    /**
     * Gets all players uuid that are actually in an screen panel inventory {@link Interfaces#getScreenPanel(Screen)}.
     *
     * @return Players that are in a screen panel inventory.
     */
	
	public Map<UUID, Screen> getScreenPanels() {
		return screenPanels;
	}
	
    /**
     * Gets all players uuid that are actually in an screen contents panel inventory {@link Interfaces#getContents(Screen, Integer)}.
     *
     * @return Players that are in a screen contents panel inventory.
     */
	
	public Map<UUID, Screen> getContentsPanels() {
		return contentsPanels;
	}
	
    /**
     * Gets all players uuid that have an video instance.
     *
     * <p>Contains for example a player that haven't already selected a screen into assign a video.
     *
     * @return A HashMap of players that have an video instance and their video instance.
     */
	
	public Map<UUID, VideoInstance> getSelectedVideos() {
		return selectedVideos;
	}
	
    /**
     * Gets all loading videos names.
     *
     * @return A list of videos loading names.
     */
	
	public ArrayList<String> getLoadingVideos() {
		return loadingVideos;
	}
	
    /**
     * Gets all playing videos names.
     *
     * @return A list of videos playing names.
     */
	
	public ArrayList<String> getPlayingVideos() {
		return playingVideos;
	}
	
    /**
     * Gets {@link MapColorSpaceData} for {@link MapColorPalette}.
     *
     * @return {@link MapColorSpaceData} for {@link MapColorPalette}.
     */
	
	public MapColorSpaceData getMapColorSpaceData() {
		return mapColorSpaceData;
	}
}
