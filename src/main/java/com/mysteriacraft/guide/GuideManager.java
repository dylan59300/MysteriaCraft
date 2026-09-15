package com.mysteriacraft.guide;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Charge les sujets du guide depuis guide.yml et suit, en SQLite, les joueurs l'ayant deja vu
 * (pour ne l'ouvrir automatiquement qu'a la toute premiere connexion de chacun).
 */
public class GuideManager {

    /** Un sujet du guide : son icone, son nom affiche, et sa description (lore). */
    public record GuideTopic(String id, String displayName, Material icon, List<String> lore) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager guideConfig;

    /** LinkedHashMap : l'ordre de declaration dans guide.yml fixe l'ordre d'affichage dans le menu. */
    private final Map<String, GuideTopic> topics = new LinkedHashMap<>();

    public GuideManager(Plugin plugin, Database database, ConfigManager guideConfig) {
        this.plugin = plugin;
        this.database = database;
        this.guideConfig = guideConfig;
        createTable();
        loadConfig();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS guide_vus (uuid TEXT NOT NULL PRIMARY KEY);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'guide_vus' : " + e.getMessage());
        }
    }

    public void loadConfig() {
        topics.clear();
        ConfigurationSection root = guideConfig.get().getConfigurationSection("sujets");
        if (root == null) {
            plugin.getLogger().warning("Aucun sujet trouve (section 'sujets' manquante dans guide.yml).");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String displayName = section.getString("nom", id);
            Material icon = Material.matchMaterial(section.getString("icone", "PAPER"));
            if (icon == null) {
                icon = Material.PAPER;
            }
            List<String> lore = section.getStringList("lore");
            topics.put(id.toLowerCase(), new GuideTopic(id.toLowerCase(), displayName, icon, lore));
        }
        plugin.getLogger().info(topics.size() + " sujet(s) de guide charge(s) depuis guide.yml.");
    }

    public List<GuideTopic> getTopics() {
        return new ArrayList<>(topics.values());
    }

    /** True si ce joueur n'a jamais vu le guide (marque comme vu par le meme appel). */
    public synchronized boolean markSeenAndCheckFirstTime(UUID uuid) {
        String select = "SELECT 1 FROM guide_vus WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return false;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture 'guide_vus' pour " + uuid + " : " + e.getMessage());
            return false;
        }

        String insert = "INSERT INTO guide_vus (uuid) VALUES (?) ON CONFLICT(uuid) DO NOTHING;";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur sauvegarde 'guide_vus' pour " + uuid + " : " + e.getMessage());
        }
        return true;
    }
}
