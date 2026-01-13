package com._650a.movietheatrecore.configuration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.util.Host;

/** 
* The Configuration class allow a direct bridge between plugin configuration and
* the game itself, it mainly contains getter methods for messages, and some plugin
* parameters and other usefull tools between plugin folder and running application.
*
*<p>Configuration class can be define anyware as basic constructor, wich then grant acces
* to all the getter methods.
*
* @author  hwic
* @version 1.0
* @since   2021-08-23 
*/

public class Configuration {

	private final Main plugin = Main.getPlugin(Main.class);
	private static final char[] LEGACY_FOLDER_NAME = new char[] {
			'M', 'e', 'd', 'i', 'a', 'P', 'l', 'a', 'y', 'e', 'r'
	};

	private static final String DEFAULT_LANGUAGE = "EN";

	private final File configurationFile = new File(plugin.getDataFolder() + "/configuration/", "configuration.yml");
	private final File translationFile = new File(plugin.getDataFolder() + "/translations/", "EN.yml");	

	private final File videosFolder = new File(plugin.getDataFolder() + "/videos/");
	private final File screensFolder = new File(plugin.getDataFolder() + "/screens/");
	private final File mapsFolder = new File(plugin.getDataFolder() + "/images/maps/");
	private final File mediaCacheFolder = new File(plugin.getDataFolder() + "/cache/videos/");
	private final File resourcePackFolder = new File(plugin.getDataFolder() + "/resourcepacks/");
	private final File audioChunksFolder = new File(plugin.getDataFolder() + "/audio/");
	private final File theatreFolder = new File(plugin.getDataFolder() + "/theatre/");
	private final File tmpFolder = new File(plugin.getDataFolder() + "/tmp/");
	
	private FileConfiguration fileconfiguration;
	private final java.util.List<String> migrationNotes = new java.util.ArrayList<>();
	
    /**
     * Creates the configuration file containing all plugin parametters and messages.
     *
     *<p>Setup should be called once on the server startup, and shall not be effective if
     * the configuration file exists, otherwise delete the configuration file to obtain
     * a new one.
     *
     * @throws IOException When failed or interrupted I/O operations occurs.
     * @throws InvalidConfigurationException When non-respect of YAML syntax.
     */
	
	public void setup() {

		if(!configurationFile.exists()) {
			configurationFile.getParentFile().mkdirs();

			fileconfiguration = new YamlConfiguration();

			fileconfiguration.set("general.auto-update", true);
			fileconfiguration.set("general.auto-update-libraries", true);
			fileconfiguration.set("general.update-url", plugin.getDescription().getWebsite());
			fileconfiguration.set("general.force-permissions", true);
			fileconfiguration.set("general.external-communication", true);
			fileconfiguration.set("general.packet-compression", true);
			fileconfiguration.set("general.alternative-server", "none");
	    	fileconfiguration.set("general.language", DEFAULT_LANGUAGE);

		    Host host = new Host(Bukkit.getServer().getIp());
		    if(host.getOfficials().contains(host.getCountryCode())) fileconfiguration.set("general.language", host.getCountryCode());

			fileconfiguration.set("general.ping-sound", true);
			fileconfiguration.set("general.verify-files-on-load", true);
			fileconfiguration.set("general.save-streams", false);

			fileconfiguration.set("general.maximum-distance-to-receive", 10);
			fileconfiguration.set("general.maximum-playing-videos", 5);
			fileconfiguration.set("general.maximum-loading-videos", 1);
			fileconfiguration.set("general.remove-screen-structure-on-restart", false);
			fileconfiguration.set("general.remove-screen-structure-on-end", false);

			fileconfiguration.set("video.screen-block", "BARRIER");
			fileconfiguration.set("video.visible-screen-frames-support", false);
			fileconfiguration.set("video.glowing-screen-frames-support", false);

			fileconfiguration.set("sources.allowlist-mode", "OFF");
			fileconfiguration.set("sources.allowed-domains", java.util.Collections.emptyList());
			fileconfiguration.set("sources.max-download-mb", 1024);
			fileconfiguration.set("sources.download-timeout-seconds", 30);
			fileconfiguration.set("sources.cache-max-gb", 5);
			fileconfiguration.set("sources.youtube-resolver-path", "");
			fileconfiguration.set("sources.youtube-cookies-path", "");
			fileconfiguration.set("sources.youtube-extra-args", java.util.Collections.emptyList());
			fileconfiguration.set("sources.ffprobe-path", "plugins/MovieTheatreCore/runtime/bin/ffprobe");
			fileconfiguration.set("sources.ffmpeg-path", "plugins/MovieTheatreCore/runtime/bin/ffmpeg");
			fileconfiguration.set("sources.deno-path", "");

			fileconfiguration.set("dependencies.prefer-system.ffmpeg", true);
			fileconfiguration.set("dependencies.prefer-system.ffprobe", true);
			fileconfiguration.set("dependencies.prefer-system.yt-dlp", true);
			fileconfiguration.set("dependencies.prefer-system.deno", true);
			fileconfiguration.set("dependencies.paths.ffmpeg", "");
			fileconfiguration.set("dependencies.paths.ffprobe", "");
			fileconfiguration.set("dependencies.paths.ytdlp", "");
			fileconfiguration.set("dependencies.paths.deno", "");
			fileconfiguration.set("dependencies.install.directory", "runtime/bin");
			fileconfiguration.set("dependencies.install.exec-directory", "/home/container/MovieTheatreCore/runtime/bin");
			fileconfiguration.set("dependencies.install.min-free-mb", 512);
			fileconfiguration.set("dependencies.install.auto-install", true);
			fileconfiguration.set("dependencies.install.auto-update", true);
			fileconfiguration.set("dependencies.install.update-check-hours", 24);

			fileconfiguration.set("youtube.use-js-runtime", true);
			fileconfiguration.set("youtube.cookies-path", "plugins/MovieTheatreCore/youtube-cookies.txt");
			fileconfiguration.set("youtube.require-cookies", false);

			fileconfiguration.set("audio.chunk-seconds", 2);
			fileconfiguration.set("audio.codec", "vorbis");
			fileconfiguration.set("audio.sample-rate", 48000);

			fileconfiguration.set("theatre.enabled", true);
			fileconfiguration.set("theatre.max-shows", 5);
			fileconfiguration.set("theatre.tick-interval", 1);
			fileconfiguration.set("theatre.audio-update-interval", 1);
			fileconfiguration.set("theatre.audience-check-interval", 20);
			fileconfiguration.set("theatre.default-zone-radius", 16);
			fileconfiguration.set("theatre.schedule-check-interval-seconds", 30);

			fileconfiguration.set("pack.public-base-url", "");
			fileconfiguration.set("resource_pack.url", "");
			fileconfiguration.set("resource_pack.sha1", "");
			fileconfiguration.set("resource_pack.assets-hash", "");
			fileconfiguration.set("resource_pack.last-build", 0L);
			fileconfiguration.set("resource_pack.server.enabled", true);
			fileconfiguration.set("resource_pack.server.bind", "0.0.0.0");
			fileconfiguration.set("resource_pack.server.port", 8123);
			fileconfiguration.set("resource_pack.server.public-url", "");

			fileconfiguration.set("debug.render", false);
			fileconfiguration.set("debug.pack", false);
			fileconfiguration.set("debug.screens", false);

			fileconfiguration.set("advanced.delete-frames-on-loaded", false);
			fileconfiguration.set("advanced.delete-video-on-loaded", false);
			fileconfiguration.set("advanced.detect-duplicated-frames", false);
			fileconfiguration.set("advanced.ressemblance-to-skip", 100);
			fileconfiguration.set("advanced.system", com._650a.movietheatrecore.system.System.getSystemType().toString());
			fileconfiguration.set("advanced.tmp-dir", "plugins/MovieTheatreCore/tmp");
			
			try {
				fileconfiguration.save(configurationFile);
			}catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(!videosFolder.exists()) {
			videosFolder.mkdir();
		}
		if(!screensFolder.exists()) {
			screensFolder.mkdir();
		}
		if(!mapsFolder.exists()) {
			mapsFolder.mkdirs();
		}
		if(!mediaCacheFolder.exists()) {
			mediaCacheFolder.mkdirs();
		}
		if(!resourcePackFolder.exists()) {
			resourcePackFolder.mkdirs();
		}
		if(!audioChunksFolder.exists()) {
			audioChunksFolder.mkdirs();
		}
		if(!theatreFolder.exists()) {
			theatreFolder.mkdirs();
		}
		if(!tmpFolder.exists()) {
			tmpFolder.mkdirs();
		}
		new File(plugin.getDataFolder() + "/translations/").mkdirs();
	}
	
    /**
     * Gets an FileConfiguration instance of the configuration-file.
     *
     * <p>Called on every {@link Configuration} getter method, for
     * parameters.
     *
     * @return FileConfiguration instance of the configuration-file.
     */
	
	public FileConfiguration getConfigFile() {

		fileconfiguration = new YamlConfiguration();
		
		try {
			fileconfiguration.load(configurationFile);
			if(migrateConfiguration(fileconfiguration)) {
				fileconfiguration.save(configurationFile);
				Bukkit.getLogger().info("[MovieTheatreCore]: configuration.yml migrated with new defaults (existing values preserved).");
				for (String note : migrationNotes) {
					Bukkit.getLogger().info("[MovieTheatreCore]: " + note);
				}
			}
		}catch (InvalidConfigurationException e) {
			Bukkit.getLogger().warning("[MovieTheatreCore]: Invalid configuration.yml. Check for unquoted wildcards like *.domain.com; allowlist entries will be ignored until fixed.");
			fileconfiguration = new YamlConfiguration();
		}catch (IOException e) {
			e.printStackTrace();
		}
		return fileconfiguration;
    }
	
    /**
     * Gets an FileConfiguration instance of the messages-file.
     *
     * <p>Called on every {@link Configuration} getter method, for
     * messages.
     *
     * @return FileConfiguration instance of the messages-file.
     */
	
	public FileConfiguration getMessagesFile() {
		
		String language = plugin_langage();
		File translationFile = new File(plugin.getDataFolder() + "/translations/", language + ".yml");	
		if(!translationFile.exists()) translationFile = this.translationFile;
		
		fileconfiguration = new YamlConfiguration();
		
		try {
			fileconfiguration.load(translationFile);
		}catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}
		return fileconfiguration;
    }
	
    /**
     * Gets the videos folder, which containing all relative informations
     * and data about all the videos.
     * 
     * @return Videos folder containing all videos and their own data.
     */
	
	public File getVideosFolder() {
		return videosFolder;
	}
	
    /**
     * Gets the screens folder, which containing all relative informations
     * and data about all the screens.
     * 
     * @return Screens folder containing all screens and their own data.
     */
	
	public File getScreensFolder() {
		return screensFolder;
	}

	public File getMediaCacheFolder() {
		return mediaCacheFolder;
	}

	public File getResourcePackFolder() {
		return resourcePackFolder;
	}

	public File getAudioChunksFolder() {
		return audioChunksFolder;
	}

	public File getTheatreFolder() {
		return theatreFolder;
	}
	
    /**
     * Translate alternate color codes such as Ampersand into Minecraft
     * colors.
     *
     * @param a The non Minecraft colored message.
     * @return The Minecraft colored message.
     */
	
	public String getMessage(String a) {
		if(a == null) {
			return "";
		}
		return ChatColor.translateAlternateColorCodes('&', a);
	}
	
    /**
     * Translate alternate color codes such as Ampersand into Minecraft
     * colors, and replace requested holder by real value.
     *
     * @param a The non Minecraft colored message.
     * @param b The replacing value.
     * @return The Minecraft colored message within the replaced value.
     */
	
	public String getMessage(String a, String b) {
		if(a == null) {
			return "";
		}

		if(a.contains("%video%")) {
			a = a.replaceAll("%video%", b);
		}
		
		if(a.contains("%offset%")) {
			a = a.replaceAll("%offset%", b);
		}
		
		if(a.contains("%screen%")) {
			a = a.replaceAll("%screen%", b);
		}
		
		if(a.contains("%index%")) {
			a = a.replaceAll("%index%", b);
		}
		
		if(a.contains("%dimension%")) {
			a = a.replaceAll("%dimension%", b);
		}
		
		if(a.contains("%time%")) {
			a = a.replaceAll("%time%", b);
		}
		
		if(a.contains("%image%")) {
			a = a.replaceAll("%image%", b);
		}
		
		if(a.contains("%url%")) {
			a = a.replaceAll("%url%", b);
		}
		
		if(a.contains("%player%")) {
			a = a.replaceAll("%player%", b);
		}
		
		if(a.contains("%tasks%")) {
			a = a.replaceAll("%tasks%", b);
		}
		return ChatColor.translateAlternateColorCodes('&', a);
	}
	
    /**
     * Translate alternate color codes such as Ampersand into Minecraft
     * colors, and replace all requested holders by real values.
     *
     * @param a The non Minecraft colored message.
     * @param b A replacing value.
     * @param c A replacing value.
     * @return The Minecraft colored message within the replaced values.
     */
	
	public String getMessage(String a, String b, String c) {
		if(a == null) {
			return "";
		}

		if(a.contains("%video%")) {
			a = a.replaceAll("%video%", b);
		}
		
		if(a.contains("%image%")) {
			a = a.replaceAll("%image%", b);
		}
		
		if(a.contains("%description%")) {
			a = a.replaceAll("%description%", c);
		}
		
		if(a.contains("%framerate%")) {
			a = a.replaceAll("%framerate%", c);
		}
		
		if(a.contains("%speed%")) {
			a = a.replaceAll("%speed%", c);
		}
		
		if(a.contains("%volume%")) {
			a = a.replaceAll("%volume%", c);
		}
		
		if(a.contains("%offset%")) {
			a = a.replaceAll("%offset%", c);
		}
		
		if(a.contains("%time%")) {
			a = a.replaceAll("%time%", c);
		}
		
		if(a.contains("%id")) {
			a = a.replaceAll("%id%", c);
		}
		
		if(a.contains("%player")) {
			a = a.replaceAll("%player%", c);
		}
		return ChatColor.translateAlternateColorCodes('&', a);
	}
	
	//ALL GETTER METHODS THAT CAN BE CALLED FOR OBTAINING CONFIGURATION INFORMATIONS AND MESSAGES//
	
	public boolean plugin_auto_update() {
		return getBooleanValue("general.auto-update", "plugin.auto-update", true);
	}
	
	public boolean plugin_auto_update_libraries() {
		return getBooleanValue("general.auto-update-libraries", "plugin.auto-update-libraries", true);
	}

	public String plugin_update_url() {
		return getStringValue("general.update-url", "plugin.update-url", plugin.getDescription().getWebsite());
	}
	
	public boolean plugin_force_permissions() {
		return getBooleanValue("general.force-permissions", "plugin.force-permissions", true);
	}
	
	public boolean plugin_external_communication() {
		return getBooleanValue("general.external-communication", "plugin.external-communication", true);
	}
	
	public boolean plugin_packet_compression() {
		return getBooleanValue("general.packet-compression", "plugin.packet-compression", true);
	}
	
	public String plugin_alternative_server() {
		return getStringValue("general.alternative-server", "plugin.alternative-server", "none");
	}
	
	public String plugin_langage() {
		String language = getStringValue("general.language", "plugin.langage", DEFAULT_LANGUAGE);
		if(language == null || language.isBlank()) {
			language = DEFAULT_LANGUAGE;
		}
		File languageFile = new File(plugin.getDataFolder() + "/translations/", language + ".yml");
		if(!languageFile.exists()) {
			Bukkit.getLogger().warning("[MovieTheatreCore]: Missing translation " + language + ".yml, falling back to " + DEFAULT_LANGUAGE + ".");
			language = DEFAULT_LANGUAGE;
		}
		return language;
	}
	
	public boolean plugin_ping_sound() {
		return getBooleanValue("general.ping-sound", "plugin.ping-sound", true);
	}
	
	public boolean verify_files_on_load() {
		return getBooleanValue("general.verify-files-on-load", "plugin.verify-files-on-load", true);
	}
	
	public boolean frames_delete_on_loaded() {
		return getBooleanValue("advanced.delete-frames-on-loaded", "plugin.delete-frames-on-loaded", false);
	}
	
	public boolean video_delete_on_loaded() {
		return getBooleanValue("advanced.delete-video-on-loaded", "plugin.delete-video-on-loaded", false);
	}
	
	public boolean save_streams() {
		return getBooleanValue("general.save-streams", "plugin.save-streams", false);
	}
	
	public String screen_block() {
		String material = getStringValue("video.screen-block", "plugin.screen-block", "BARRIER");
		if(material.equalsIgnoreCase("BARRIER") && plugin.isOld()) material = "GLASS"; 
		return material;
	}
	
	public boolean visible_screen_frames_support() {
		return getBooleanValue("video.visible-screen-frames-support", "plugin.visible-screen-frames-support", false);
	}
	
	public boolean glowing_screen_frames_support() {
		return getBooleanValue("video.glowing-screen-frames-support", "plugin.glowing-screen-frames-support", false);
	}
	
	public int maximum_distance_to_receive() {
		return getIntValue("general.maximum-distance-to-receive", "plugin.maximum-distance-to-receive", 10);
	}

	public java.util.List<String> media_allowed_domains() {
		return getStringListValue("sources.allowed-domains", "media.allowed-domains");
	}

	public String sources_allowlist_mode() {
		return getStringValue("sources.allowlist-mode", "media.allowlist-mode", "OFF");
	}

	public long media_max_download_mb() {
		return getLongValue("sources.max-download-mb", "media.max-download-mb", 1024);
	}

	public int media_download_timeout_seconds() {
		return getIntValue("sources.download-timeout-seconds", "media.download-timeout-seconds", 30);
	}

	public long media_cache_max_gb() {
		return getLongValue("sources.cache-max-gb", "media.cache-max-gb", 5);
	}

	public String media_youtube_resolver_path() {
		return getStringValue("sources.youtube-resolver-path", "media.youtube-resolver-path", "");
	}

	public String media_youtube_cookies_path() {
		return getStringValue("sources.youtube-cookies-path", null, "");
	}

	public java.util.List<String> media_youtube_extra_args() {
		return getStringListValue("sources.youtube-extra-args", null);
	}

	public String sources_ffprobe_path() {
		return getStringValue("sources.ffprobe-path", null, "plugins/MovieTheatreCore/runtime/bin/ffprobe");
	}

	public String sources_ffmpeg_path() {
		return getStringValue("sources.ffmpeg-path", null, "plugins/MovieTheatreCore/runtime/bin/ffmpeg");
	}

	public String sources_deno_path() {
		return getStringValue("sources.deno-path", null, "");
	}

	public boolean dependencies_prefer_system_ffmpeg() {
		return getBooleanValue("dependencies.prefer-system.ffmpeg", null, true);
	}

	public boolean dependencies_prefer_system_ffprobe() {
		return getBooleanValue("dependencies.prefer-system.ffprobe", null, true);
	}

	public boolean dependencies_prefer_system_ytdlp() {
		return getBooleanValue("dependencies.prefer-system.yt-dlp", null, true);
	}

	public boolean dependencies_prefer_system_deno() {
		return getBooleanValue("dependencies.prefer-system.deno", null, true);
	}

	public String dependencies_path_ffmpeg() {
		String value = getStringValue("dependencies.paths.ffmpeg", null, "");
		return (value == null || value.isBlank()) ? sources_ffmpeg_path() : value;
	}

	public String dependencies_path_ffprobe() {
		String value = getStringValue("dependencies.paths.ffprobe", null, "");
		return (value == null || value.isBlank()) ? sources_ffprobe_path() : value;
	}

	public String dependencies_path_ytdlp() {
		String value = getStringValue("dependencies.paths.ytdlp", null, "");
		return (value == null || value.isBlank()) ? media_youtube_resolver_path() : value;
	}

	public String dependencies_path_deno() {
		String value = getStringValue("dependencies.paths.deno", null, "");
		return (value == null || value.isBlank()) ? sources_deno_path() : value;
	}

	public String dependencies_install_directory() {
		return getStringValue("dependencies.install.directory", null, "");
	}

	public String dependencies_install_exec_directory() {
		return getStringValue("dependencies.install.exec-directory", null, "/home/container/MovieTheatreCore/runtime/bin");
	}

	public long dependencies_install_min_free_mb() {
		return getLongValue("dependencies.install.min-free-mb", null, 512L);
	}

	public boolean dependencies_install_auto_install() {
		if(getConfigFile().contains("dependencies.install.auto-install")) {
			return getBooleanValue("dependencies.install.auto-install", null, true);
		}
		return plugin_auto_update_libraries();
	}

	public File getTempDir() {
		String configured = getStringValue("advanced.tmp-dir", null, "plugins/MovieTheatreCore/tmp");
		File tempDir = new File(configured);
		if (!tempDir.isAbsolute() && !configured.startsWith("plugins/")) {
			tempDir = new File(plugin.getDataFolder(), configured);
		}
		if (!tempDir.exists()) {
			tempDir.mkdirs();
		}
		return tempDir;
	}

	public boolean dependencies_install_auto_update() {
		if(getConfigFile().contains("dependencies.install.auto-update")) {
			return getBooleanValue("dependencies.install.auto-update", null, true);
		}
		return plugin_auto_update_libraries();
	}

	public long dependencies_install_update_check_hours() {
		return getLongValue("dependencies.install.update-check-hours", null, 24);
	}

	public boolean youtube_use_js_runtime() {
		return getBooleanValue("youtube.use-js-runtime", null, true);
	}

	public String youtube_cookies_path() {
		String value = getStringValue("youtube.cookies-path", null, "plugins/MovieTheatreCore/youtube-cookies.txt");
		if(value == null || value.isBlank()) {
			return media_youtube_cookies_path();
		}
		return value;
	}

	public boolean youtube_require_cookies() {
		return getBooleanValue("youtube.require-cookies", null, false);
	}

	public int audio_chunk_seconds() {
		return getIntValue("audio.chunk-seconds", "audio.chunk-seconds", 2);
	}

	public String audio_codec() {
		return getStringValue("audio.codec", "audio.codec", "vorbis");
	}

	public int audio_sample_rate() {
		return getIntValue("audio.sample-rate", "audio.sample-rate", 48000);
	}

	public boolean theatre_enabled() {
		return getBooleanValue("theatre.enabled", null, true);
	}

	public int theatre_max_shows() {
		return getIntValue("theatre.max-shows", null, 5);
	}

	public int theatre_tick_interval() {
		return getIntValue("theatre.tick-interval", null, 1);
	}

	public int theatre_audio_update_interval() {
		return getIntValue("theatre.audio-update-interval", null, 1);
	}

	public int theatre_audience_check_interval() {
		return getIntValue("theatre.audience-check-interval", null, 20);
	}

	public int theatre_default_zone_radius() {
		return getIntValue("theatre.default-zone-radius", null, 16);
	}

	public int theatre_schedule_check_interval_seconds() {
		return getIntValue("theatre.schedule-check-interval-seconds", null, 30);
	}

	public String pack_public_base_url() {
		String value = getStringValue("pack.public-base-url", null, "");
		if (value != null && value.endsWith("/")) {
			return value.substring(0, value.length() - 1);
		}
		return value;
	}

	public String resourcepack_host_url() {
		return getStringValue("resource_pack.url", "resourcepack.host-url", "");
	}

	public String resourcepack_sha1() {
		return getStringValue("resource_pack.sha1", "resourcepack.sha1", "");
	}

	public String resourcepack_assets_hash() {
		return getStringValue("resource_pack.assets-hash", null, "");
	}

	public long resourcepack_last_build() {
		return getLongValue("resource_pack.last-build", null, 0L);
	}

	public boolean resourcepack_server_enabled() {
		return getBooleanValue("resource_pack.server.enabled", null, true);
	}

	public String resourcepack_server_bind() {
		return getStringValue("resource_pack.server.bind", null, "0.0.0.0");
	}

	public int resourcepack_server_port() {
		return getIntValue("resource_pack.server.port", null, 8123);
	}

	public String resourcepack_server_public_url() {
		return getStringValue("resource_pack.server.public-url", null, "");
	}

	public boolean debug_render() {
		return getBooleanValue("debug.render", null, false);
	}

	public boolean debug_pack() {
		return getBooleanValue("debug.pack", null, false);
	}

	public boolean debug_screens() {
		return getBooleanValue("debug.screens", null, false);
	}

	public void set_resourcepack_sha1(String sha1) {
		fileconfiguration = new YamlConfiguration();
		try {
			fileconfiguration.load(configurationFile);
			fileconfiguration.set("resource_pack.sha1", sha1);
			fileconfiguration.save(configurationFile);
		}catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}
	}

	public void set_resourcepack_assets_hash(String hash) {
		fileconfiguration = new YamlConfiguration();
		try {
			fileconfiguration.load(configurationFile);
			fileconfiguration.set("resource_pack.assets-hash", hash);
			fileconfiguration.save(configurationFile);
		}catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}
	}

	public void set_resourcepack_last_build(long timestamp) {
		fileconfiguration = new YamlConfiguration();
		try {
			fileconfiguration.load(configurationFile);
			fileconfiguration.set("resource_pack.last-build", timestamp);
			fileconfiguration.save(configurationFile);
		}catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}
	}
	
	public int maximum_playing_videos() {
		return getIntValue("general.maximum-playing-videos", "plugin.maximum-playing-videos", 5);
	}
	
	public int maximum_loading_videos() {
		return getIntValue("general.maximum-loading-videos", "plugin.maximum-loading-videos", 1);
	}
	
	public boolean remove_screen_on_end() {
		return getBooleanValue("general.remove-screen-structure-on-end", "plugin.remove-screen-structure-on-end", false);
	}
	
	public boolean remove_screen_on_restart() {
		return getBooleanValue("general.remove-screen-structure-on-restart", "plugin.remove-screen-structure-on-restart", false);
	}
	
	public boolean detect_duplicated_frames() {
		return getBooleanValue("advanced.detect-duplicated-frames", "plugin.detect-duplicated-frames", false);
	}
	
	public double ressemblance_to_skip() {
		return getDoubleValue("advanced.ressemblance-to-skip", "plugin.ressemblance-to-skip", 100);
	}

	private boolean migrateConfiguration(FileConfiguration configuration) {
		migrationNotes.clear();
		boolean changed = false;
		changed |= ensureString(configuration, "general.language", "plugin.langage", DEFAULT_LANGUAGE);
		changed |= ensureBoolean(configuration, "general.auto-update", "plugin.auto-update", true);
		changed |= ensureBoolean(configuration, "general.auto-update-libraries", "plugin.auto-update-libraries", true);
		changed |= ensureString(configuration, "general.update-url", "plugin.update-url", plugin.getDescription().getWebsite());
		changed |= ensureBoolean(configuration, "general.force-permissions", "plugin.force-permissions", true);
		changed |= ensureBoolean(configuration, "general.external-communication", "plugin.external-communication", true);
		changed |= ensureBoolean(configuration, "general.packet-compression", "plugin.packet-compression", true);
		changed |= ensureString(configuration, "general.alternative-server", "plugin.alternative-server", "none");
		changed |= ensureBoolean(configuration, "general.ping-sound", "plugin.ping-sound", true);
		changed |= ensureBoolean(configuration, "general.verify-files-on-load", "plugin.verify-files-on-load", true);
		changed |= ensureBoolean(configuration, "general.save-streams", "plugin.save-streams", false);
		changed |= ensureInt(configuration, "general.maximum-distance-to-receive", "plugin.maximum-distance-to-receive", 10);
		changed |= ensureInt(configuration, "general.maximum-playing-videos", "plugin.maximum-playing-videos", 5);
		changed |= ensureInt(configuration, "general.maximum-loading-videos", "plugin.maximum-loading-videos", 1);
		changed |= ensureBoolean(configuration, "general.remove-screen-structure-on-restart", "plugin.remove-screen-structure-on-restart", false);
		changed |= ensureBoolean(configuration, "general.remove-screen-structure-on-end", "plugin.remove-screen-structure-on-end", false);

		changed |= ensureString(configuration, "video.screen-block", "plugin.screen-block", "BARRIER");
		changed |= ensureBoolean(configuration, "video.visible-screen-frames-support", "plugin.visible-screen-frames-support", false);
		changed |= ensureBoolean(configuration, "video.glowing-screen-frames-support", "plugin.glowing-screen-frames-support", false);

		boolean hasAllowlistMode = configuration.contains("sources.allowlist-mode") || configuration.contains("media.allowlist-mode");
		changed |= ensureStringList(configuration, "sources.allowed-domains", "media.allowed-domains");
		changed |= ensureString(configuration, "sources.allowlist-mode", "media.allowlist-mode", "OFF");
		changed |= ensureLong(configuration, "sources.max-download-mb", "media.max-download-mb", 1024);
		changed |= ensureInt(configuration, "sources.download-timeout-seconds", "media.download-timeout-seconds", 30);
		changed |= ensureLong(configuration, "sources.cache-max-gb", "media.cache-max-gb", 5);
		changed |= ensureString(configuration, "sources.youtube-resolver-path", "media.youtube-resolver-path", "");
		changed |= ensureString(configuration, "sources.youtube-cookies-path", null, "");
		changed |= ensureStringList(configuration, "sources.youtube-extra-args", null);
		changed |= ensureString(configuration, "sources.ffprobe-path", null, "plugins/MovieTheatreCore/runtime/bin/ffprobe");
		changed |= ensureString(configuration, "sources.ffmpeg-path", null, "plugins/MovieTheatreCore/runtime/bin/ffmpeg");
		changed |= ensureString(configuration, "sources.deno-path", null, "");
		changed |= ensureBoolean(configuration, "dependencies.prefer-system.ffmpeg", null, true);
		changed |= ensureBoolean(configuration, "dependencies.prefer-system.ffprobe", null, true);
		changed |= ensureBoolean(configuration, "dependencies.prefer-system.yt-dlp", null, true);
		changed |= ensureBoolean(configuration, "dependencies.prefer-system.deno", null, true);
		changed |= ensureString(configuration, "dependencies.paths.ffmpeg", "sources.ffmpeg-path", "");
		changed |= ensureString(configuration, "dependencies.paths.ffprobe", "sources.ffprobe-path", "");
		changed |= ensureString(configuration, "dependencies.paths.ytdlp", "sources.youtube-resolver-path", "");
		changed |= ensureString(configuration, "dependencies.paths.deno", "sources.deno-path", "");
		changed |= ensureString(configuration, "dependencies.install.directory", null, "runtime/bin");
		changed |= ensureString(configuration, "dependencies.install.exec-directory", null, "/home/container/MovieTheatreCore/runtime/bin");
		changed |= ensureLong(configuration, "dependencies.install.min-free-mb", null, 512L);
		changed |= ensureBoolean(configuration, "dependencies.install.auto-install", "general.auto-update-libraries", true);
		changed |= ensureBoolean(configuration, "dependencies.install.auto-update", "general.auto-update-libraries", true);
		changed |= ensureLong(configuration, "dependencies.install.update-check-hours", null, 24L);
		changed |= ensureBoolean(configuration, "youtube.use-js-runtime", null, true);
		changed |= ensureString(configuration, "youtube.cookies-path", "sources.youtube-cookies-path", "plugins/MovieTheatreCore/youtube-cookies.txt");
		changed |= ensureBoolean(configuration, "youtube.require-cookies", null, false);

		if(!hasAllowlistMode) {
			java.util.List<String> allowedDomains = configuration.getStringList("sources.allowed-domains");
			String mode = (allowedDomains != null && !allowedDomains.isEmpty()) ? "STRICT" : "OFF";
			configuration.set("sources.allowlist-mode", mode);
			migrationNotes.add("Updated sources.allowlist-mode to " + mode + " (derived from allowed domains).");
			changed = true;
		}

		changed |= ensureInt(configuration, "audio.chunk-seconds", "audio.chunk-seconds", 2);
		changed |= ensureString(configuration, "audio.codec", "audio.codec", "vorbis");
		changed |= ensureInt(configuration, "audio.sample-rate", "audio.sample-rate", 48000);

		changed |= ensureBoolean(configuration, "theatre.enabled", null, true);
		changed |= ensureInt(configuration, "theatre.max-shows", null, 5);
		changed |= ensureInt(configuration, "theatre.tick-interval", null, 1);
		changed |= ensureInt(configuration, "theatre.audio-update-interval", null, 1);
		changed |= ensureInt(configuration, "theatre.audience-check-interval", null, 20);
		changed |= ensureInt(configuration, "theatre.default-zone-radius", null, 16);
		changed |= ensureInt(configuration, "theatre.schedule-check-interval-seconds", null, 30);

		changed |= ensurePackPublicBaseUrl(configuration);
		changed |= ensureString(configuration, "resource_pack.url", "resourcepack.host-url", "");
		changed |= ensureString(configuration, "resource_pack.sha1", "resourcepack.sha1", "");
		changed |= ensureString(configuration, "resource_pack.assets-hash", null, "");
		changed |= ensureLong(configuration, "resource_pack.last-build", null, 0L);
		changed |= ensureBoolean(configuration, "resource_pack.server.enabled", null, true);
		changed |= ensureString(configuration, "resource_pack.server.bind", null, "0.0.0.0");
		changed |= ensureInt(configuration, "resource_pack.server.port", null, 8123);
		changed |= ensureString(configuration, "resource_pack.server.public-url", null, "");

		changed |= ensureBoolean(configuration, "debug.render", null, false);
		changed |= ensureBoolean(configuration, "debug.pack", null, false);
		changed |= ensureBoolean(configuration, "debug.screens", null, false);

		changed |= ensureBoolean(configuration, "advanced.delete-frames-on-loaded", "plugin.delete-frames-on-loaded", false);
		changed |= ensureBoolean(configuration, "advanced.delete-video-on-loaded", "plugin.delete-video-on-loaded", false);
		changed |= ensureBoolean(configuration, "advanced.detect-duplicated-frames", "plugin.detect-duplicated-frames", false);
		changed |= ensureDouble(configuration, "advanced.ressemblance-to-skip", "plugin.ressemblance-to-skip", 100);
		changed |= ensureString(configuration, "advanced.system", "plugin.system",
				com._650a.movietheatrecore.system.System.getSystemType().toString());
		changed |= ensureString(configuration, "advanced.tmp-dir", null, "plugins/MovieTheatreCore/tmp");

		changed |= migratePathValue(configuration, "sources.ffprobe-path");
		changed |= migratePathValue(configuration, "sources.ffmpeg-path");
		changed |= migratePathValue(configuration, "sources.youtube-cookies-path");
		changed |= migratePathValue(configuration, "youtube.cookies-path");
		changed |= migratePathValue(configuration, "dependencies.install.directory");
		changed |= migratePathValue(configuration, "dependencies.install.exec-directory");
		changed |= migratePathValue(configuration, "advanced.tmp-dir");
		if (configuration.contains("audio.enabled")) {
			migrationNotes.add("Deprecated key detected: audio.enabled (audio is now always automatic; the value is ignored).");
		}
		return changed;
	}

	private boolean ensureString(FileConfiguration configuration, String newKey, String legacyKey, String defaultValue) {
		if(configuration.contains(newKey)) {
			return false;
		}
		String value = legacyKey == null ? null : configuration.getString(legacyKey);
		if(value == null || value.isEmpty()) {
			value = defaultValue;
		}
		configuration.set(newKey, value);
		recordMigration(newKey, value, legacyKey, configuration);
		return true;
	}

	private boolean migratePathValue(FileConfiguration configuration, String key) {
		if (!configuration.contains(key)) {
			return false;
		}
		String value = configuration.getString(key);
		if (value == null || value.isEmpty()) {
			return false;
		}
		String legacyFolder = "plugins/" + getLegacyFolderName();
		String migrated = value.replace(legacyFolder, "plugins/MovieTheatreCore")
				.replace(legacyFolder.toLowerCase(Locale.ROOT), "plugins/MovieTheatreCore");
		if (!migrated.equals(value)) {
			configuration.set(key, migrated);
			migrationNotes.add("Updated " + key + " to " + migrated + " (migrated legacy path).");
			return true;
		}
		return false;
	}

	private String getLegacyFolderName() {
		return new String(LEGACY_FOLDER_NAME);
	}

	private boolean ensureBoolean(FileConfiguration configuration, String newKey, String legacyKey, boolean defaultValue) {
		if(configuration.contains(newKey)) {
			return false;
		}
		boolean value = legacyKey != null && configuration.contains(legacyKey)
				? configuration.getBoolean(legacyKey)
				: defaultValue;
		configuration.set(newKey, value);
		recordMigration(newKey, value, legacyKey, configuration);
		return true;
	}

	private boolean ensureInt(FileConfiguration configuration, String newKey, String legacyKey, int defaultValue) {
		if(configuration.contains(newKey)) {
			return false;
		}
		int value = legacyKey != null && configuration.contains(legacyKey)
				? configuration.getInt(legacyKey)
				: defaultValue;
		configuration.set(newKey, value);
		recordMigration(newKey, value, legacyKey, configuration);
		return true;
	}

	private boolean ensureLong(FileConfiguration configuration, String newKey, String legacyKey, long defaultValue) {
		if(configuration.contains(newKey)) {
			return false;
		}
		long value = legacyKey != null && configuration.contains(legacyKey)
				? configuration.getLong(legacyKey)
				: defaultValue;
		configuration.set(newKey, value);
		recordMigration(newKey, value, legacyKey, configuration);
		return true;
	}

	private boolean ensureDouble(FileConfiguration configuration, String newKey, String legacyKey, double defaultValue) {
		if(configuration.contains(newKey)) {
			return false;
		}
		double value = legacyKey != null && configuration.contains(legacyKey)
				? configuration.getDouble(legacyKey)
				: defaultValue;
		configuration.set(newKey, value);
		recordMigration(newKey, value, legacyKey, configuration);
		return true;
	}

	private boolean ensureStringList(FileConfiguration configuration, String newKey, String legacyKey) {
		if(configuration.contains(newKey)) {
			return false;
		}
		java.util.List<String> value = legacyKey != null && configuration.contains(legacyKey)
				? configuration.getStringList(legacyKey)
				: java.util.Collections.emptyList();
		configuration.set(newKey, value);
		recordMigration(newKey, value, legacyKey, configuration);
		return true;
	}

	private boolean ensurePackPublicBaseUrl(FileConfiguration configuration) {
		if (configuration.contains("pack.public-base-url")) {
			return false;
		}
		String legacy = configuration.getString("resource_pack.server.public-url");
		if (legacy == null || legacy.isBlank()) {
			legacy = configuration.getString("resource_pack.url");
			if (legacy != null && legacy.endsWith("/pack.zip")) {
				legacy = legacy.substring(0, legacy.length() - "/pack.zip".length());
			}
		}
		String value = legacy == null ? "" : legacy;
		configuration.set("pack.public-base-url", value);
		recordMigration("pack.public-base-url", value, "resource_pack.server.public-url", configuration);
		return true;
	}

	private void recordMigration(String key, Object value, String legacyKey, FileConfiguration configuration) {
		boolean fromLegacy = legacyKey != null && configuration.contains(legacyKey);
		String source = fromLegacy ? (" (from " + legacyKey + ")") : "";
		migrationNotes.add("Added " + key + " = " + String.valueOf(value) + source + ".");
	}

	private String getStringValue(String newKey, String legacyKey, String defaultValue) {
		FileConfiguration configuration = getConfigFile();
		if(configuration.contains(newKey)) {
			String value = configuration.getString(newKey);
			return (value == null || value.isEmpty()) ? defaultValue : value;
		}
		if(legacyKey != null && configuration.contains(legacyKey)) {
			String value = configuration.getString(legacyKey);
			return (value == null || value.isEmpty()) ? defaultValue : value;
		}
		return defaultValue;
	}

	private boolean getBooleanValue(String newKey, String legacyKey, boolean defaultValue) {
		FileConfiguration configuration = getConfigFile();
		if(configuration.contains(newKey)) {
			return configuration.getBoolean(newKey);
		}
		if(legacyKey != null && configuration.contains(legacyKey)) {
			return configuration.getBoolean(legacyKey);
		}
		return defaultValue;
	}

	private int getIntValue(String newKey, String legacyKey, int defaultValue) {
		FileConfiguration configuration = getConfigFile();
		if(configuration.contains(newKey)) {
			return configuration.getInt(newKey);
		}
		if(legacyKey != null && configuration.contains(legacyKey)) {
			return configuration.getInt(legacyKey);
		}
		return defaultValue;
	}

	private long getLongValue(String newKey, String legacyKey, long defaultValue) {
		FileConfiguration configuration = getConfigFile();
		if(configuration.contains(newKey)) {
			return configuration.getLong(newKey);
		}
		if(legacyKey != null && configuration.contains(legacyKey)) {
			return configuration.getLong(legacyKey);
		}
		return defaultValue;
	}

	private double getDoubleValue(String newKey, String legacyKey, double defaultValue) {
		FileConfiguration configuration = getConfigFile();
		if(configuration.contains(newKey)) {
			return configuration.getDouble(newKey);
		}
		if(legacyKey != null && configuration.contains(legacyKey)) {
			return configuration.getDouble(legacyKey);
		}
		return defaultValue;
	}

	private java.util.List<String> getStringListValue(String newKey, String legacyKey) {
		FileConfiguration configuration = getConfigFile();
		if(configuration.contains(newKey)) {
			return configuration.getStringList(newKey);
		}
		if(legacyKey != null && configuration.contains(legacyKey)) {
			return configuration.getStringList(legacyKey);
		}
		return java.util.Collections.emptyList();
	}
	
	public String plugin_outdated() {
		return getMessage(getMessagesFile().getString("messages.plugin-outdated"));
	}
	
	public String video_load_requested() {
		return getMessage(getMessagesFile().getString("messages.video-load-requested"));
	}
		
	public String video_load_notice() {
		return getMessage(getMessagesFile().getString("messages.video-load-notice"));
	}
	
	public String video_offset_notice(String video) {
		return getMessage(getMessagesFile().getString("messages.video-offset-notice"), video);
	}
	
	public String video_offset_start(String offset) {
		return getMessage(getMessagesFile().getString("messages.video-offset-start"), offset);
	}
	
	public String video_unloaded(String video) {
		return getMessage(getMessagesFile().getString("messages.video-unloaded"), video);
	}
	
	public String video_downloaded(String video) {
		return getMessage(getMessagesFile().getString("messages.video-downloaded"), video);
	}
	
	public String video_deleted(String video) {
		return getMessage(getMessagesFile().getString("messages.video-deleted"), video);
	}
	
	public String video_already_loaded(String video) {
		return getMessage(getMessagesFile().getString("messages.video-already-loaded"), video);
	}
	
	public String video_already_loading(String video) {
		return getMessage(getMessagesFile().getString("messages.video-already-loading"), video);
	}
	
	public String video_not_loaded(String video) {
		return getMessage(getMessagesFile().getString("messages.video-not-loaded"), video);
	}
	
	public String video_selected(String video) {
		return getMessage(getMessagesFile().getString("messages.video-selected"), video);
	}
	
	public String video_assigned(String video) {
		return getMessage(getMessagesFile().getString("messages.video-assigned"), video);
	}
	
	public String video_not_enought_space(String video) {
		return getMessage(getMessagesFile().getString("messages.video-not-enought-space"), video);
	}
	
	public String video_description_updated(String video, String description) {
		return getMessage(getMessagesFile().getString("messages.video-description-updated"), video, description);
	}
	
	public String video_framerate_updated(String video, String framerate) {
		return getMessage(getMessagesFile().getString("messages.video-framerate-updated"), video, framerate);
	}
	
	public String video_speed_updated(String video, String speed) {
		return getMessage(getMessagesFile().getString("messages.video-speed-updated"), video, speed);
	}
	
	public String video_compress_enabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-compress-enabled"), video);
	}
	
	public String video_compress_disabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-compress-disabled"), video);
	}
	
	public String video_volume_updated(String video, String volume) {
		return getMessage(getMessagesFile().getString("messages.video-volume-updated"), video, volume);
	}
	
	public String video_audio_offset_updated(String video, String volume) {
		return getMessage(getMessagesFile().getString("messages.video-audio-offset-updated"), video, volume);
	}
	
	public String video_age_limit_disabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-age-limit-disabled"), video);
	}
	
	public String video_age_limit_enabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-age-limit-enabled"), video);
	}
	
	public String video_audio_disabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-audio-disabled"), video);
	}
	
	public String video_audio_enabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-audio-enabled"), video);
	}
	
	public String video_loop_enabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-loop-enabled"), video);
	}
	
	public String video_loop_disabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-loop-disabled"), video);
	}
	
	public String video_real_time_rendering_enabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-real-time-rendering-enabled"), video);
	}
	
	public String video_real_time_rendering_disabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-real-time-rendering-disabled"), video);
	}
	
	public String video_skip_frames_enabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-skip-frames-enabled"), video);
	}
	
	public String video_skip_frames_disabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-skip-frames-disabled"), video);
	}
	
	public String video_skip_duplicated_frames_enabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-skip-duplicated-frames-enabled"), video);
	}
	
	public String video_skip_duplicated_frames_disabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-skip-duplicated-frames-disabled"), video);
	}
	
	public String video_show_informations_enabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-show-informations-enabled"), video);
	}
	
	public String video_show_informations_disabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-show-informations-disabled"), video);
	}
	
	public String video_show_fps_enabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-show-fps-enabled"), video);
	}
	
	public String video_show_fps_disabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-show-fps-disabled"), video);
	}
	
	public String video_run_on_startup_enabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-run-on-startup-enabled"), video);
	}
	
	public String video_run_on_startup_disabled(String video) {
		return getMessage(getMessagesFile().getString("messages.video-run-on-startup-disabled"), video);
	}
	
	public String video_processing_frames_starting(String video) {
		return getMessage(getMessagesFile().getString("messages.video-processing-frames-starting"), video);
	}
	
	public String video_processing_frames_finished(String video) {
		return getMessage(getMessagesFile().getString("messages.video-processing-frames-finished"), video);
	}
	
	public String video_processing_estimated_time(String time) {
		return getMessage(getMessagesFile().getString("messages.video-processing-estimated-time"), time);
	}
	
	public String video_processing_audio_starting(String video) {
		return getMessage(getMessagesFile().getString("messages.video-processing-audio-starting"), video);
	}
	
	public String video_processing_audio_finished(String video) {
		return getMessage(getMessagesFile().getString("messages.video-processing-audio-finished"), video);
	}
	
	public String video_processing_minecraft_starting(String video) {
		return getMessage(getMessagesFile().getString("messages.video-processing-minecraft-starting"), video);
	}
	
	public String video_processing_minecraft_finished(String video) {
		return getMessage(getMessagesFile().getString("messages.video-processing-minecraft-finished"), video);
	}
	
	public String video_processing_finished(String video, String time) {
		return getMessage(getMessagesFile().getString("messages.video-processing-finished"), video, time);
	}
	
	public String video_instance_started(String video, String id) {
		return getMessage(getMessagesFile().getString("messages.video-instance-started"), video, id);
	}
	
	public String video_instance_stopped(String video) {
		return getMessage(getMessagesFile().getString("messages.video-instance-stopped"), video);
	}
	
	public String video_instance_pause(String video) {
		return getMessage(getMessagesFile().getString("messages.video-instance-pause"), video);
	}
	
	public String video_instance_resume(String video) {
		return getMessage(getMessagesFile().getString("messages.video-instance-resume"), video);
	}
	
	public String video_instance_not_on_screen() {
		return getMessage(getMessagesFile().getString("messages.video-instance-not-on-screen"));
	}
	
	public String videos_empty_registered() {
		return getMessage(getMessagesFile().getString("messages.videos-empty-registered"));
	}
	
	public String videos_canceled_tasks(String tasks) {
		return getMessage(getMessagesFile().getString("messages.videos-canceled-tasks"), tasks);
	}
	
	public String videos_notice() {
		return getMessage(getMessagesFile().getString("messages.videos-notice"));
	}
	
	public String screen_created(String dimension) {
		return getMessage(getMessagesFile().getString("messages.screen-created"), dimension);
	}
	
	public String screen_removed(String index) {
		return getMessage(getMessagesFile().getString("messages.screen-removed"), index);
	}
	
	public String screen_deleted(String name) {
		return getMessage(getMessagesFile().getString("messages.screen-deleted"), name);
	}
	
	public String screen_teleport(String name) {
		return getMessage(getMessagesFile().getString("messages.screen-teleport"), name);
	}
	
	public String screen_invalid_index(String index) {
		return getMessage(getMessagesFile().getString("messages.screen-invalid-index"), index);
	}
	
	public String screen_cannot_create() {
		return getMessage(getMessagesFile().getString("messages.screen-cannot-create"));
	}
	
	public String screen_not_ready(String player) {
		return getMessage(getMessagesFile().getString("messages.screen-not-ready"), player);
	}
	
	public String screen_everyone_ready() {
		return getMessage(getMessagesFile().getString("messages.screen-everyone-ready"));
	}
	
	public String image_rendered(String image) {
		return getMessage(getMessagesFile().getString("messages.image-rendered"), image);
	}
	
	public String image_deleted(String image) {
		return getMessage(getMessagesFile().getString("messages.image-deleted"), image);
	}
	
	public String image_placed(String image) {
		return getMessage(getMessagesFile().getString("messages.image-placed"), image);
	}
	
	public String image_removed(String image) {
		return getMessage(getMessagesFile().getString("messages.image-removed"), image);
	}
	
	public String image_received(String image) {
		return getMessage(getMessagesFile().getString("messages.image-received"), image);
	}
	
	public String image_gived(String image, String player) {
		return getMessage(getMessagesFile().getString("messages.image-gived"), image, player);
	}
	
	public String image_not_found() {
		return getMessage(getMessagesFile().getString("messages.image-not-found"));
	}
	
	public String image_invalid() {
		return getMessage(getMessagesFile().getString("messages.image-invalid"));
	}
	
	public String image_cannot_read() {
		return getMessage(getMessagesFile().getString("messages.image-cannot-read"));
	}
	
	public String image_invalid_screen() {
		return getMessage(getMessagesFile().getString("messages.image-invalid-screen"));
	}
	
	public String image_already_rendered(String image) {
		return getMessage(getMessagesFile().getString("messages.image-already-rendered"), image);
	}
	
	public String item_previous_page_name() {
		return getMessage(getMessagesFile().getString("messages.item.previous-page.name"));
	}
	
	public String item_previous_page_lore() {
		return getMessage(getMessagesFile().getString("messages.item.previous-page.lore"));
	}
	
	public String item_refresh_page_name() {
		return getMessage(getMessagesFile().getString("messages.item.refresh-page.name"));
	}
	
	public String item_refresh_page_lore() {
		return getMessage(getMessagesFile().getString("messages.item.refresh-page.lore"));
	}
	
	public String item_next_page_name() {
		return getMessage(getMessagesFile().getString("messages.item.next-page.name"));
	}
	
	public String item_next_page_lore() {
		return getMessage(getMessagesFile().getString("messages.item.next-page.lore"));
	}
	
	public String item_play_name() {
		return getMessage(getMessagesFile().getString("messages.item.play.name"));
	}
	
	public String item_play_lore() {
		return getMessage(getMessagesFile().getString("messages.item.play.lore"));
	}
	
	public String item_switcher_name() {
		return getMessage(getMessagesFile().getString("messages.item.switcher.name"));
	}
	
	public String item_switcher_lore() {
		return getMessage(getMessagesFile().getString("messages.item.switcher.lore"));
	}
	
	public String item_remote_name() {
		return getMessage(getMessagesFile().getString("messages.item.remote.name"));
	}
	
	public String item_remote_lore() {
		return getMessage(getMessagesFile().getString("messages.item.remote.lore"));
	}

	public String item_admin_tool_name() {
		return getMessage(getMessagesFile().getString("messages.item.admin-tool.name"));
	}

	public String item_admin_tool_lore() {
		return getMessage(getMessagesFile().getString("messages.item.admin-tool.lore"));
	}

	public String legacy_command_notice() {
		return getMessage(getMessagesFile().getString("messages.legacy-command"));
	}
	
	public String item_load_name() {
		return getMessage(getMessagesFile().getString("messages.item.load.name"));
	}
	
	public String item_load_lore() {
		return getMessage(getMessagesFile().getString("messages.item.load.lore"));
	}
	
	public String item_teleport_name() {
		return getMessage(getMessagesFile().getString("messages.item.teleport.name"));
	}
	
	public String item_teleport_lore() {
		return getMessage(getMessagesFile().getString("messages.item.teleport.lore"));
	}
	
	public String item_delete_name() {
		return getMessage(getMessagesFile().getString("messages.item.delete.name"));
	}
	
	public String item_delete_lore_1() {
		return getMessage(getMessagesFile().getString("messages.item.delete.lore-1"));
	}
	
	public String item_delete_lore_2() {
		return getMessage(getMessagesFile().getString("messages.item.delete.lore-2"));
	}
	
	public String item_remove_name() {
		return getMessage(getMessagesFile().getString("messages.item.remove.name"));
	}
	
	public String item_remove_lore_1() {
		return getMessage(getMessagesFile().getString("messages.item.remove.lore-1"));
	}
	
	public String item_remove_lore_2() {
		return getMessage(getMessagesFile().getString("messages.item.remove.lore-2"));
	}
	
	public String item_poster_lore_1() {
		return getMessage(getMessagesFile().getString("messages.item.poster.lore-1"));
	}
	
	public String item_poster_lore_2() {
		return getMessage(getMessagesFile().getString("messages.item.poster.lore-2"));
	}
	
	public String item_poster_lore_3() {
		return getMessage(getMessagesFile().getString("messages.item.poster.lore-3"));
	}
	
	public String item_poster_lore_4() {
		return getMessage(getMessagesFile().getString("messages.item.poster.lore-4"));
	}
	
	public String libraries_not_installed() {
		return getMessage(getMessagesFile().getString("messages.libraries-not-installed"));
	}
	
	public String age_limit_warning() {
		return getMessage(getMessagesFile().getString("messages.age-limit-warning"));
	}
	
	public String incompatible() {
		return getMessage(getMessagesFile().getString("messages.incompatible"));
	}
	
	public String impossible_connection() {
		return getMessage(getMessagesFile().getString("messages.impossible-connection"));
	}
	
	public String no_page_left() {
		return getMessage(getMessagesFile().getString("messages.no-page-left"));
	}
	
	public String too_much_loading() {
		return getMessage(getMessagesFile().getString("messages.too-much-loading"));
	}
	
	public String too_much_playing() {
		return getMessage(getMessagesFile().getString("messages.too-much-playing"));
	}
	
	public String no_screen_playing() {
		return getMessage(getMessagesFile().getString("messages.no-screen-playing"));
	}
	
	public String videos_reload_requested() {
		return getMessage(getMessagesFile().getString("messages.videos-reload-requested"));
	}
	
	public String screens_reload_requested() {
		return getMessage(getMessagesFile().getString("messages.screens-reload-requested"));
	}
	
	public String videos_reloaded() {
		return getMessage(getMessagesFile().getString("messages.videos-reloaded"));
	}
	
	public String screens_reloaded() {
		return getMessage(getMessagesFile().getString("messages.screens-reloaded"));
	}
	
	public String video_invalid(String video) {
		return getMessage(getMessagesFile().getString("messages.video-invalid"), video);
	}
	
	public String video_invalid_index(String index) {
		return getMessage(getMessagesFile().getString("messages.video-invalid-index"), index);
	}
	
	public String not_number() {
		return getMessage(getMessagesFile().getString("messages.not-number"));
	}
	
	public String available_images() {
		return getMessage(getMessagesFile().getString("messages.available-images"));
	}
	
	public String available_videos() {
		return getMessage(getMessagesFile().getString("messages.available-videos"));
	}
	
	public String loaded() {
		return getMessage(getMessagesFile().getString("messages.loaded"));
	}
	
	public String not_loaded() {
		return getMessage(getMessagesFile().getString("messages.not-loaded"));
	}
	
	public String negative_number() {
		return getMessage(getMessagesFile().getString("messages.negative-number"));
	}
	
	public String offline_player(String player) {
		return getMessage(getMessagesFile().getString("messages.offline-player"), player);
	}
	
	public String invalid_url(String url) {
		return getMessage(getMessagesFile().getString("messages.invalid-url"), url);
	}
	
	public String invalid_sender() {
		return getMessage(getMessagesFile().getString("messages.invalid-sender"));
	}
	
	public String insufficient_permissions() {
		return getMessage(getMessagesFile().getString("messages.insufficient-permissions"));
	}
}
