package com.mysteriacraft.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Charge et recharge un fichier YAML de configuration situe dans le dossier du plugin.
 * Copie automatiquement la version par defaut embarquee dans le jar si le fichier n'existe pas.
 */
public class ConfigManager {

    private final Plugin plugin;
    private final String fileName;
    private final File file;
    private FileConfiguration config;

    public ConfigManager(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);
        load();
    }

    public void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.saveResource(fileName, false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        // Fusionne avec les valeurs par defaut du jar pour ajouter les cles manquantes
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder " + fileName + " : " + e.getMessage());
        }
    }

    public FileConfiguration get() {
        return config;
    }
}
