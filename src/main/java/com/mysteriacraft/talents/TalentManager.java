package com.mysteriacraft.talents;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
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
 * Arbre de talents : chaque kill donne 1 point de talent (voir TalentListener), depensable pour
 * debloquer un noeud (voir talents.yml), qui accorde un bonus permanent (degats, vente Boutique,
 * vitesse de minage, ou vie max). Un noeud peut exiger qu'un autre soit deja debloque
 * (prerequis). Points et noeuds debloques sont persistes en SQLite, sans cache memoire (petit
 * volume, lu uniquement a l'ouverture du menu/au deblocage).
 */
public class TalentManager {

    public enum EffetType {
        BONUS_DEGATS, BONUS_VENTE, BONUS_VITESSE_MINAGE, BONUS_VIE_MAX
    }

    public record TalentNode(String id, String nom, String description, ItemStack icon,
                              int coutPoints, String prerequis, EffetType effet, double valeur) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager talentsConfig;
    private final Map<String, TalentNode> nodes = new LinkedHashMap<>();

    public TalentManager(Plugin plugin, Database database, ConfigManager talentsConfig) {
        this.plugin = plugin;
        this.database = database;
        this.talentsConfig = talentsConfig;
        createTables();
        loadNodes();
    }

    private void createTables() {
        String points = "CREATE TABLE IF NOT EXISTS talents_points (uuid TEXT PRIMARY KEY, points INTEGER NOT NULL DEFAULT 0);";
        String debloques = "CREATE TABLE IF NOT EXISTS talents_debloques (uuid TEXT NOT NULL, noeud_id TEXT NOT NULL, " +
                "PRIMARY KEY (uuid, noeud_id));";
        Connection connection = database.getConnection();
        try (PreparedStatement s1 = connection.prepareStatement(points);
             PreparedStatement s2 = connection.prepareStatement(debloques)) {
            s1.executeUpdate();
            s2.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation tables de talents : " + e.getMessage());
        }
    }

    public void loadNodes() {
        nodes.clear();
        ConfigurationSection root = talentsConfig.get().getConfigurationSection("noeuds");
        if (root == null) {
            plugin.getLogger().warning("Aucun talent trouve (section 'noeuds' manquante).");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String nom = section.getString("nom", id);
            String description = section.getString("description", "");
            Material iconMaterial = Material.matchMaterial(section.getString("icone", "PAPER"));
            if (iconMaterial == null) {
                iconMaterial = Material.PAPER;
            }
            ItemStack icon = new ItemBuilder(iconMaterial).name(nom).build();
            int cout = Math.max(1, section.getInt("cout-points", 1));
            String prerequis = section.getString("prerequis", null);
            EffetType effet;
            try {
                effet = EffetType.valueOf(section.getString("effet.type", "BONUS_DEGATS").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Effet de talent invalide pour '" + id + "'.");
                continue;
            }
            double valeur = section.getDouble("effet.valeur", 1.0);
            nodes.put(id.toLowerCase(), new TalentNode(id.toLowerCase(), nom, description, icon, cout, prerequis, effet, valeur));
        }
        plugin.getLogger().info(nodes.size() + " talent(s) charge(s).");
    }

    public List<TalentNode> getNodesSorted() {
        return new ArrayList<>(nodes.values());
    }

    public TalentNode getNode(String id) {
        return id == null ? null : nodes.get(id.toLowerCase());
    }

    /** Requete synchrone : a appeler hors du thread principal. */
    public int getPoints(UUID uuid) {
        String select = "SELECT points FROM talents_points WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("points") : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture points de talent pour " + uuid + " : " + e.getMessage());
            return 0;
        }
    }

    public void addPoints(UUID uuid, int amount) {
        int current = getPoints(uuid);
        setPoints(uuid, current + amount);
    }

    private void setPoints(UUID uuid, int amount) {
        String upsert = "INSERT INTO talents_points (uuid, points) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET points = excluded.points;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, Math.max(0, amount));
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur sauvegarde points de talent pour " + uuid + " : " + e.getMessage());
        }
    }

    public boolean isUnlocked(UUID uuid, String nodeId) {
        String select = "SELECT 1 FROM talents_debloques WHERE uuid = ? AND noeud_id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, nodeId.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture talent debloque pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    public List<String> getUnlockedNodeIds(UUID uuid) {
        List<String> ids = new ArrayList<>();
        String select = "SELECT noeud_id FROM talents_debloques WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("noeud_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture talents debloques pour " + uuid + " : " + e.getMessage());
        }
        return ids;
    }

    /** Tente de debloquer ce noeud : deduit les points si les conditions sont remplies. Renvoie
     * true en cas de succes. Requete synchrone : a appeler hors du thread principal. */
    public boolean unlock(UUID uuid, String nodeId) {
        TalentNode node = getNode(nodeId);
        if (node == null || isUnlocked(uuid, nodeId)) {
            return false;
        }
        if (node.prerequis() != null && !isUnlocked(uuid, node.prerequis())) {
            return false;
        }
        int points = getPoints(uuid);
        if (points < node.coutPoints()) {
            return false;
        }
        setPoints(uuid, points - node.coutPoints());

        String insert = "INSERT OR IGNORE INTO talents_debloques (uuid, noeud_id) VALUES (?, ?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, node.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deblocage talent '" + nodeId + "' pour " + uuid + " : " + e.getMessage());
            return false;
        }
        return true;
    }

    private double sumBonus(UUID uuid, EffetType type) {
        double total = 0;
        for (String nodeId : getUnlockedNodeIds(uuid)) {
            TalentNode node = getNode(nodeId);
            if (node != null && node.effet() == type) {
                total += node.valeur();
            }
        }
        return total;
    }

    public double getBonusVentePourcent(UUID uuid) {
        return sumBonus(uuid, EffetType.BONUS_VENTE);
    }

    public double getBonusDegats(UUID uuid) {
        return sumBonus(uuid, EffetType.BONUS_DEGATS);
    }

    public double getBonusVieMax(UUID uuid) {
        return sumBonus(uuid, EffetType.BONUS_VIE_MAX);
    }

    /** Niveau (amplificateur) de Haste permanent accorde par les talents "BONUS_VITESSE_MINAGE"
     * debloques, 0 si aucun. */
    public int getNiveauVitesseMinage(UUID uuid) {
        return (int) sumBonus(uuid, EffetType.BONUS_VITESSE_MINAGE);
    }
}
