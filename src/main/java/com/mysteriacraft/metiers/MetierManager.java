package com.mysteriacraft.metiers;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Metiers (pecheur/forgeron/alchimiste, voir metiers.yml) : un joueur en choisit UN a la fois
 * (changer de metier reinitialise sa progression). Chaque metier monte de niveau avec de l'xp
 * gagnee via des actions liees (peche, artisanat a l'Etabli, consommation de potions - voir les
 * listeners/services concernes), et accorde un bonus scalant avec le niveau.
 */
public class MetierManager {

    public record MetierDefinition(String id, String nom, String description, long xpParNiveau,
                                    int niveauMax, double valeurParNiveau) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager metiersConfig;
    private final Map<String, MetierDefinition> metiers = new LinkedHashMap<>();

    public MetierManager(Plugin plugin, Database database, ConfigManager metiersConfig) {
        this.plugin = plugin;
        this.database = database;
        this.metiersConfig = metiersConfig;
        createTable();
        loadMetiers();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS metiers_joueurs (" +
                "uuid TEXT PRIMARY KEY, metier_id TEXT NOT NULL, xp INTEGER NOT NULL DEFAULT 0);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'metiers_joueurs' : " + e.getMessage());
        }
    }

    public void loadMetiers() {
        metiers.clear();
        ConfigurationSection root = metiersConfig.get().getConfigurationSection("metiers");
        if (root == null) {
            plugin.getLogger().warning("Aucun metier trouve (section 'metiers' manquante).");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String nom = section.getString("nom", id);
            String description = section.getString("description", "");
            long xpParNiveau = Math.max(1, section.getLong("xp-par-niveau", 100));
            int niveauMax = Math.max(1, section.getInt("niveau-max", 20));
            double valeurParNiveau = section.getDouble("valeur-par-niveau", 1.0);
            metiers.put(id.toLowerCase(), new MetierDefinition(id.toLowerCase(), nom, description, xpParNiveau, niveauMax, valeurParNiveau));
        }
        plugin.getLogger().info(metiers.size() + " metier(s) charge(s).");
    }

    public MetierDefinition getMetier(String id) {
        return id == null ? null : metiers.get(id.toLowerCase());
    }

    public Map<String, MetierDefinition> getMetiers() {
        return metiers;
    }

    /** Requete synchrone : a appeler hors du thread principal. */
    public String getMetierId(UUID uuid) {
        String select = "SELECT metier_id FROM metiers_joueurs WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString("metier_id") : null;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture metier pour " + uuid + " : " + e.getMessage());
            return null;
        }
    }

    public long getXp(UUID uuid) {
        String select = "SELECT xp FROM metiers_joueurs WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("xp") : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture xp de metier pour " + uuid + " : " + e.getMessage());
            return 0;
        }
    }

    public int getNiveau(UUID uuid) {
        MetierDefinition metier = getMetier(getMetierId(uuid));
        if (metier == null) {
            return 0;
        }
        return (int) Math.min(metier.niveauMax(), getXp(uuid) / metier.xpParNiveau());
    }

    /** Bonus scalant avec le niveau (interprete differemment selon le metier : voir les services
     * qui l'utilisent), 0 si aucun metier choisi. */
    public double getBonus(UUID uuid) {
        return getNiveau(uuid) * getValeurParNiveau(uuid);
    }

    private double getValeurParNiveau(UUID uuid) {
        MetierDefinition metier = getMetier(getMetierId(uuid));
        return metier == null ? 0 : metier.valeurParNiveau();
    }

    /** Choisit ce metier (reinitialise l'xp si different du metier actuel). Requete synchrone :
     * a appeler hors du thread principal. */
    public void choisir(UUID uuid, String metierId) {
        String upsert = "INSERT INTO metiers_joueurs (uuid, metier_id, xp) VALUES (?, ?, 0) " +
                "ON CONFLICT(uuid) DO UPDATE SET metier_id = excluded.metier_id, xp = 0;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, metierId.toLowerCase());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur choix de metier pour " + uuid + " : " + e.getMessage());
        }
    }

    public void addXp(UUID uuid, long amount) {
        String update = "UPDATE metiers_joueurs SET xp = xp + ? WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setLong(1, amount);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur ajout xp de metier pour " + uuid + " : " + e.getMessage());
        }
    }
}
