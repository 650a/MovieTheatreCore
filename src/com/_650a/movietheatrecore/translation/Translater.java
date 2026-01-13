package com._650a.movietheatrecore.translation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.bukkit.Bukkit;

import com._650a.movietheatrecore.Main;

/** 
* The Translater class is used in {@link Main} in order to extract plugin langage translations.
* It consists in a single method, see {@link #createTranslationFile(String)}.
* 
* @author  hwic
* @version 1.0.0
* @since   2022-07-16 
*/

public class Translater {
	
	private Main plugin = Main.getPlugin(Main.class);

    /**
     * Ensure the selected translation file is exported from the jar file.
     *
     * @param language The language code to export.
     * @return The resolved language code used.
     */
	
    public String ensureTranslationExported(String language) {
        String resolvedLanguage = normalizeLanguage(language);
        File translationsFolder = new File(plugin.getDataFolder(), "translations");
        translationsFolder.mkdirs();

        String resourcePath = resourcePathFor(resolvedLanguage);
        if(!resourceExists(resourcePath)) {
            Bukkit.getLogger().warning("[MovieTheatreCore]: Missing bundled translation " + resolvedLanguage + ".yml, falling back to EN.");
            resolvedLanguage = "EN";
            resourcePath = resourcePathFor(resolvedLanguage);
        }

        if(!resourceExists(resourcePath)) {
            Bukkit.getLogger().warning("[MovieTheatreCore]: Missing bundled translation EN.yml; translations will not be exported.");
            return resolvedLanguage;
        }

        File target = new File(translationsFolder, resolvedLanguage + ".yml");
        if(!target.exists()) {
            plugin.saveResource(resourcePath, false);
        }

        return resolvedLanguage;
    }

    private String normalizeLanguage(String language) {
        if(language == null || language.isBlank()) {
            return "EN";
        }
        return language.trim().toUpperCase(Locale.ROOT);
    }

    private String resourcePathFor(String language) {
        return "translations/" + language + ".yml";
    }

    private boolean resourceExists(String resourcePath) {
        try(InputStream input = plugin.getResource(resourcePath)) {
            return input != null;
        }catch (IOException e) {
            return false;
        }
    }
}
