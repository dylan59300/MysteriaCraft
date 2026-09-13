package com.mysteriacraft.pets;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Charge les pets depuis pets.yml et gere leur deblocage (achat ou recompense) et le pet
 * actif de chaque joueur (un seul a la fois), persistes en SQLite.
 */
public class PetManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager petsConfig;

    private final Map<String, PetDefinition> pets = new LinkedHashMap<>();

    public PetManager(Plugin plugin, Database database, ConfigManager petsConfig) {
        this.plugin = plugin;
        this.database = database;
        this.petsConfig = petsConfig;
        createTables();
        loadPets();
    }

    private void createTables() {
        String unlockedTable = "CREATE TABLE IF NOT EXISTS pets_debloques (" +
                "uuid TEXT NOT NULL, " +
                "pet_id TEXT NOT NULL, " +
                "PRIMARY KEY (uuid, pet_id)" +
                ");";
        String activeTable = "CREATE TABLE IF NOT EXISTS pets_actifs (" +
                "uuid TEXT PRIMARY KEY, " +
                "pet_id TEXT" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(unlockedTable)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'pets_debloques' : " + e.getMessage());
        }
        try (PreparedStatement statement = connection.prepareStatement(activeTable)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'pets_actifs' : " + e.getMessage());
        }
    }

    public void loadPets() {
        pets.clear();
        ConfigurationSection root = petsConfig.get().getConfigurationSection("pets");
        if (root == null) {
            plugin.getLogger().warning("Aucun pet trouve dans pets.yml (section 'pets' manquante).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                pets.put(id.toLowerCase(), parsePet(id, section));
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement pet '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(pets.size() + " pet(s) charge(s) depuis pets.yml.");
    }

    private PetDefinition parsePet(String id, ConfigurationSection section) {
        String displayName = section.getString("nom", id);
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(section.getString("type", "WOLF").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Type d'entite inconnu pour le pet '" + id + "', WOLF utilise par defaut.");
            entityType = EntityType.WOLF;
        }
        Material icon = Material.matchMaterial(section.getString("icone", "BONE"));
        if (icon == null) {
            icon = Material.BONE;
        }
        int order = section.getInt("ordre", 0);
        List<String> lore = section.getStringList("lore");
        double price = section.getDouble("prix", 0);
        String permission = section.getString("permission", "");

        return new PetDefinition(id, displayName, entityType, icon, order, lore, price, permission);
    }

    public List<PetDefinition> getPetsSorted() {
        List<PetDefinition> sorted = new ArrayList<>(pets.values());
        sorted.sort(Comparator.comparingInt(PetDefinition::order));
        return sorted;
    }

    public PetDefinition getPet(String id) {
        return id == null ? null : pets.get(id.toLowerCase());
    }

    // ---- Deblocage ----

    public synchronized boolean isUnlocked(UUID uuid, String petId) {
        String select = "SELECT 1 FROM pets_debloques WHERE uuid = ? AND pet_id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, petId.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur verification deblocage pet '" + petId + "' pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    public synchronized void unlock(UUID uuid, String petId) {
        String insert = "INSERT OR IGNORE INTO pets_debloques (uuid, pet_id) VALUES (?, ?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, petId.toLowerCase());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deblocage pet '" + petId + "' pour " + uuid + " : " + e.getMessage());
        }
    }

    // ---- Pet actif (un seul a la fois) ----

    public synchronized String getActivePetId(UUID uuid) {
        String select = "SELECT pet_id FROM pets_actifs WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("pet_id");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture pet actif pour " + uuid + " : " + e.getMessage());
        }
        return null;
    }

    /** Definit le pet actif (petId null = aucun pet actif). */
    public synchronized void setActivePetId(UUID uuid, String petId) {
        String upsert = "INSERT INTO pets_actifs (uuid, pet_id) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET pet_id = excluded.pet_id;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, petId == null ? null : petId.toLowerCase());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour pet actif pour " + uuid + " : " + e.getMessage());
        }
    }
}
