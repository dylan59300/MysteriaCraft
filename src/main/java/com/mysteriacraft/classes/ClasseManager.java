package com.mysteriacraft.classes;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

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
 * Charge les classes/metiers depuis classes.yml et gere la classe active de chaque joueur
 * (une seule a la fois), persistee en SQLite.
 */
public class ClasseManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager classesConfig;

    private final Map<String, ClasseDefinition> classes = new LinkedHashMap<>();
    private double prixChangement;

    public ClasseManager(Plugin plugin, Database database, ConfigManager classesConfig) {
        this.plugin = plugin;
        this.database = database;
        this.classesConfig = classesConfig;
        createTable();
        loadConfig();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS joueur_classe (" +
                "uuid TEXT NOT NULL PRIMARY KEY, classe_id TEXT NOT NULL);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'joueur_classe' : " + e.getMessage());
        }
    }

    public void loadConfig() {
        classes.clear();
        prixChangement = Math.max(0, classesConfig.get().getDouble("prix-changement", 0));

        ConfigurationSection root = classesConfig.get().getConfigurationSection("classes");
        if (root == null) {
            plugin.getLogger().warning("Aucune classe trouvee dans classes.yml (section 'classes' manquante).");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                classes.put(id.toLowerCase(), parseClasse(id, section));
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement classe '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(classes.size() + " classe(s)/metier(s) charge(s) depuis classes.yml.");
    }

    private ClasseDefinition parseClasse(String id, ConfigurationSection section) {
        String displayName = section.getString("nom", id);
        Material icon = Material.matchMaterial(section.getString("icone", "PAPER"));
        if (icon == null) {
            icon = Material.PAPER;
        }
        List<String> lore = section.getStringList("lore");
        PotionEffectType effet = PotionEffectType.getByName(section.getString("effet", "SPEED"));
        if (effet == null) {
            plugin.getLogger().warning("Effet de potion inconnu pour la classe '" + id + "', SPEED utilise par defaut.");
            effet = PotionEffectType.SPEED;
        }
        int amplificateur = Math.max(0, section.getInt("amplificateur", 0));

        return new ClasseDefinition(id.toLowerCase(), displayName, icon, lore, effet, amplificateur);
    }

    public List<ClasseDefinition> getClasses() {
        return new ArrayList<>(classes.values());
    }

    public ClasseDefinition getClasse(String id) {
        return id == null ? null : classes.get(id.toLowerCase());
    }

    public double getPrixChangement() {
        return prixChangement;
    }

    // ---- Classe active du joueur ----

    public synchronized String getClasseActive(UUID uuid) {
        String select = "SELECT classe_id FROM joueur_classe WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("classe_id");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture classe active pour " + uuid + " : " + e.getMessage());
        }
        return null;
    }

    public synchronized void setClasseActive(UUID uuid, String classeId) {
        String upsert = "INSERT INTO joueur_classe (uuid, classe_id) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET classe_id = excluded.classe_id;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, classeId.toLowerCase());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour classe active pour " + uuid + " : " + e.getMessage());
        }
    }
}
