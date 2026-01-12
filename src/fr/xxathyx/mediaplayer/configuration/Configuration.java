package fr.xxathyx.mediaplayer.configuration;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.util.Host;

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

	private static final String DEFAULT_LANGUAGE = "EN";

	private final File configurationFile = new File(plugin.getDataFolder() + "/configuration/", "configuration.yml");
	private final File translationFile = new File(plugin.getDataFolder() + "/translations/", "EN.yml");	

	private final File videosFolder = new File(plugin.getDataFolder() + "/videos/");
	private final File screensFolder = new File(plugin.getDataFolder() + "/screens/");
	private final File mapsFolder = new File(plugin.getDataFolder() + "/images/maps/");
	private final File mediaCacheFolder = new File(plugin.getDataFolder() + "/cache/videos/");
	private final File resourcePackFolder = new File(plugin.getDataFolder() + "/resourcepacks/");
	private final File audioChunksFolder = new File(plugin.getDataFolder() + "/audio/");
	
	private FileConfiguration fileconfiguration;
	
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

			fileconfiguration.set("sources.allowed-domains", java.util.Collections.emptyList());
			fileconfiguration.set("sources.max-download-mb", 1024);
			fileconfiguration.set("sources.download-timeout-seconds", 30);
			fileconfiguration.set("sources.cache-max-gb", 5);
			fileconfiguration.set("sources.youtube-resolver-path", "");
			fileconfiguration.set("sources.ffprobe-path", "plugins/MediaPlayer/bin/ffprobe");
			fileconfiguration.set("sources.ffmpeg-path", "plugins/MediaPlayer/bin/ffmpeg");

			fileconfiguration.set("audio.enabled", false);
			fileconfiguration.set("audio.chunk-seconds", 2);
			fileconfiguration.set("audio.codec", "vorbis");
			fileconfiguration.set("audio.sample-rate", 48000);

			fileconfiguration.set("resource_pack.url", "");
			fileconfiguration.set("resource_pack.sha1", "");

			fileconfiguration.set("advanced.delete-frames-on-loaded", false);
			fileconfiguration.set("advanced.delete-video-on-loaded", false);
			fileconfiguration.set("advanced.detect-duplicated-frames", false);
			fileconfiguration.set("advanced.ressemblance-to-skip", 100);
			fileconfiguration.set("advanced.system", fr.xxathyx.mediaplayer.system.System.getSystemType().toString());
			
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
			}
		}catch (IOException | InvalidConfigurationException e) {
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
			Bukkit.getLogger().warning("[MediaPlayer]: Missing translation " + language + ".yml, falling back to " + DEFAULT_LANGUAGE + ".");
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

	public String sources_ffprobe_path() {
		return getStringValue("sources.ffprobe-path", null, "plugins/MediaPlayer/bin/ffprobe");
	}

	public String sources_ffmpeg_path() {
		return getStringValue("sources.ffmpeg-path", null, "plugins/MediaPlayer/bin/ffmpeg");
	}

	public boolean audio_enabled() {
		return getBooleanValue("audio.enabled", "audio.enabled", false);
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

	public String resourcepack_host_url() {
		return getStringValue("resource_pack.url", "resourcepack.host-url", "");
	}

	public String resourcepack_sha1() {
		return getStringValue("resource_pack.sha1", "resourcepack.sha1", "");
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

		changed |= ensureStringList(configuration, "sources.allowed-domains", "media.allowed-domains");
		changed |= ensureLong(configuration, "sources.max-download-mb", "media.max-download-mb", 1024);
		changed |= ensureInt(configuration, "sources.download-timeout-seconds", "media.download-timeout-seconds", 30);
		changed |= ensureLong(configuration, "sources.cache-max-gb", "media.cache-max-gb", 5);
		changed |= ensureString(configuration, "sources.youtube-resolver-path", "media.youtube-resolver-path", "");
		changed |= ensureString(configuration, "sources.ffprobe-path", null, "plugins/MediaPlayer/bin/ffprobe");
		changed |= ensureString(configuration, "sources.ffmpeg-path", null, "plugins/MediaPlayer/bin/ffmpeg");

		changed |= ensureBoolean(configuration, "audio.enabled", "audio.enabled", false);
		changed |= ensureInt(configuration, "audio.chunk-seconds", "audio.chunk-seconds", 2);
		changed |= ensureString(configuration, "audio.codec", "audio.codec", "vorbis");
		changed |= ensureInt(configuration, "audio.sample-rate", "audio.sample-rate", 48000);

		changed |= ensureString(configuration, "resource_pack.url", "resourcepack.host-url", "");
		changed |= ensureString(configuration, "resource_pack.sha1", "resourcepack.sha1", "");

		changed |= ensureBoolean(configuration, "advanced.delete-frames-on-loaded", "plugin.delete-frames-on-loaded", false);
		changed |= ensureBoolean(configuration, "advanced.delete-video-on-loaded", "plugin.delete-video-on-loaded", false);
		changed |= ensureBoolean(configuration, "advanced.detect-duplicated-frames", "plugin.detect-duplicated-frames", false);
		changed |= ensureDouble(configuration, "advanced.ressemblance-to-skip", "plugin.ressemblance-to-skip", 100);
		changed |= ensureString(configuration, "advanced.system", "plugin.system",
				fr.xxathyx.mediaplayer.system.System.getSystemType().toString());
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
		return true;
	}

	private boolean ensureBoolean(FileConfiguration configuration, String newKey, String legacyKey, boolean defaultValue) {
		if(configuration.contains(newKey)) {
			return false;
		}
		boolean value = legacyKey != null && configuration.contains(legacyKey)
				? configuration.getBoolean(legacyKey)
				: defaultValue;
		configuration.set(newKey, value);
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
		return true;
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
