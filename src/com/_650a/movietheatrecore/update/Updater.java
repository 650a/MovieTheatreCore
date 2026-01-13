package com._650a.movietheatrecore.update;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import com._650a.movietheatrecore.Main;
import com._650a.movietheatrecore.configuration.Configuration;

/** 
* The Updater class, is uses to check for update and update the plugin in the main class,
* seen {@link Main#onEnable()}.
*
* @author  hwic
* @version 1.0.0
* @since   2021-08-23 
*/

public class Updater {

    public enum UpdateStatus {
        UP_TO_DATE,
        OUTDATED,
        UNREACHABLE,
        DISABLED
    }

    public static class UpdateCheckResult {
        private final UpdateStatus status;
        private final String message;
        private final String url;

        UpdateCheckResult(UpdateStatus status, String message, String url) {
            this.status = status;
            this.message = message;
            this.url = url;
        }

        public UpdateStatus getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public String getUrl() {
            return url;
        }

        public boolean isOutdated() {
            return status == UpdateStatus.OUTDATED;
        }
    }

    private final Main plugin = Main.getPlugin(Main.class);
    private final Configuration configuration = new Configuration();

    private boolean disabled;
    private boolean warnedNotFound;

    /** 
    * Updates the plugin if it is outdated, the server need to be connected to internet. The
    * {@link Configuration#plugin_auto_update()}
    * have to return true in order to download the plugin jar.
    * 
    * Update isn't done asynchronously, and not effective after the plugin download in order to avoid reload issues.
    * After download the plugin will be stored in the plugin/update folder, and will replace the oldest jar on the
    * next server restart.
    * 
    * <p>This is the main updater method, because it uses all other ones, and see {@link #checkForUpdates(boolean)},
    * {@link #download()}.
    */

    public void update() {
        if (configuration.plugin_external_communication()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                UpdateCheckResult result = checkForUpdates(false);
                if (result.isOutdated()) {
                    Updater.createFolders();
                    if (configuration.plugin_auto_update()) {
                        if (!download()) {
                            return;
                        }
                        Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.DARK_GRAY + "[MovieTheatreCore]: " + ChatColor.GREEN
                                + "The new plugin version has been downloaded, and will be applied on the next server restart.");
                        return;
                    }
                    Bukkit.getLogger().warning("[MovieTheatreCore]: Plugin version is out of date. Please enable auto-update in configuration,"
                            + " or update it manually from: " + configuration.plugin_update_url());
                    return;
                }
                if (result.getStatus() == UpdateStatus.UP_TO_DATE) {
                    Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.DARK_GRAY + "[MovieTheatreCore]: " + ChatColor.GREEN
                            + "You are using the last version of the plugin (" + plugin.getDescription().getVersion() + ")");
                }
            });
        }
    }

    public UpdateCheckResult checkForUpdates(boolean verbose) {
        if (disabled) {
            return new UpdateCheckResult(UpdateStatus.DISABLED, "Updater disabled (previous 404).", configuration.plugin_update_url());
        }
        try {
            File jar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
            URL onlineJar = new URL(configuration.plugin_update_url());
            HttpURLConnection connection = (HttpURLConnection) onlineJar.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code == 404) {
                disableForNotFound(onlineJar);
                return new UpdateCheckResult(UpdateStatus.DISABLED, "Update URL returned 404.", configuration.plugin_update_url());
            }
            if (code < 200 || code >= 400) {
                if (verbose) {
                    Bukkit.getLogger().warning("[MovieTheatreCore]: Couldn't verify plugin version (HTTP " + code + ").");
                }
                return new UpdateCheckResult(UpdateStatus.UNREACHABLE, "HTTP " + code, configuration.plugin_update_url());
            }
            long onlineLength = connection.getContentLengthLong();
            if (onlineLength <= 0) {
                return new UpdateCheckResult(UpdateStatus.UNREACHABLE, "Missing content length.", configuration.plugin_update_url());
            }
            if (onlineLength != jar.length()) {
                return new UpdateCheckResult(UpdateStatus.OUTDATED, "Remote size differs from local jar.", configuration.plugin_update_url());
            }
            return new UpdateCheckResult(UpdateStatus.UP_TO_DATE, "Jar sizes match.", configuration.plugin_update_url());
        } catch (Exception e) {
            if (verbose) {
                Bukkit.getLogger().warning("[MovieTheatreCore]: Couldn't verify plugin version, try again later. " + e.getMessage());
            }
            return new UpdateCheckResult(UpdateStatus.UNREACHABLE, e.getMessage(), configuration.plugin_update_url());
        }
    }

    /** 
    * Downloads the latest version of the plugin.
    * 
    * The server need to be connected to internet to download the jar from the configured update URL.
    * 
    * @throws IOException When failed or interrupted I/O operations occurs.
    * @throws URISyntaxException When the oldest jar locating is failed.
    */

    public boolean download() {

        try {
            File jar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
            File newJar = new File(plugin.getDataFolder().getParentFile() + "/update/" + jar.getName());

            URL onlineJar = new URL(configuration.plugin_update_url());
            HttpURLConnection connection = (HttpURLConnection) onlineJar.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code == 404) {
                disableForNotFound(onlineJar);
                return false;
            }
            if (code < 200 || code >= 400) {
                Bukkit.getLogger().warning("[MovieTheatreCore]: Couldn't download the new version of the plugin (HTTP " + code + ").");
                return false;
            }

            try (InputStream input = connection.getInputStream()) {
                Files.copy(input, newJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            if (newJar.exists() && calculateFileHash(newJar).equals(calculateFileHash(jar))) {
                newJar.delete();
                Bukkit.getLogger().info("[MovieTheatreCore]: You are using the latest plugin version : " + plugin.getDescription().getVersion());
                return false;
            }

        } catch (Exception e) {
            Bukkit.getLogger().warning("[MovieTheatreCore]: Couldn't download the new version of the plugin, download it manually.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void disableForNotFound(URL url) {
        disabled = true;
        if (!warnedNotFound) {
            warnedNotFound = true;
            Bukkit.getLogger().warning("[MovieTheatreCore]: Update URL returned 404 and has been disabled for this session: " + url);
        }
    }

    /** 
    * Creates plugins update folder if doesn't doesn't exists yet.
    * 
    * <p>This method is called everytime the {@link #update()} detects an outdated plugin version, but if the folder
    * already exists nothing is done.
    */

    public static void createFolders() {

        File receptionFolder = new File(Main.getPlugin(Main.class).getDataFolder().getParentFile() + "/update/");

        if (!(receptionFolder.exists())) {
            receptionFolder.mkdir();
        }
    }

    public static String calculateFileHash(File file) throws Exception {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] fileBytes = Files.readAllBytes(file.toPath());
        byte[] hashBytes = digest.digest(fileBytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
