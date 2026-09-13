package com.mysteriacraft.battlepass;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Charge les paliers du BattlePass depuis battlepass.yml et gere la progression des joueurs
 * (xp, statut premium, recompenses reclamees) en SQLite. Saison permanente : pas de reset.
 */
public class BattlePassManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager battlepassConfig;

    private final List<BattlePassLevel> levels = new ArrayList<>();
    private long xpPerMinute = 0;
    private double premiumPrice = 0;

    public BattlePassManager(Plugin plugin, Database database, ConfigManager battlepassConfig) {
        this.plugin = plugin;
        this.database = database;
        this.battlepassConfig = battlepassConfig;
        createTables();
        loadLevels();
    }

    private void createTables() {
        String playersTable = "CREATE TABLE IF NOT EXISTS battlepass_joueurs (" +
                "uuid TEXT PRIMARY KEY, " +
                "xp INTEGER NOT NULL DEFAULT 0, " +
                "premium INTEGER NOT NULL DEFAULT 0" +
                ");";
        String claimsTable = "CREATE TABLE IF NOT EXISTS battlepass_reclamations (" +
                "uuid TEXT NOT NULL, " +
                "niveau INTEGER NOT NULL, " +
                "piste TEXT NOT NULL, " +
                "PRIMARY KEY (uuid, niveau, piste)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(playersTable)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'battlepass_joueurs' : " + e.getMessage());
        }
        try (PreparedStatement statement = connection.prepareStatement(claimsTable)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'battlepass_reclamations' : " + e.getMessage());
        }
    }

    public void loadLevels() {
        levels.clear();
        xpPerMinute = battlepassConfig.get().getLong("xp-par-minute-de-jeu", 0);
        premiumPrice = battlepassConfig.get().getDouble("prix-premium", 0);

        ConfigurationSection root = battlepassConfig.get().getConfigurationSection("niveaux");
        if (root == null) {
            plugin.getLogger().warning("Aucun palier trouve dans battlepass.yml (section 'niveaux' manquante).");
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                int level = Integer.parseInt(key);
                long xpRequired = section.getLong("xp-requis", 0);
                BattlePassReward free = parseReward(section.getConfigurationSection("gratuit"));
                BattlePassReward premium = parseReward(section.getConfigurationSection("premium"));
                levels.add(new BattlePassLevel(level, xpRequired, free, premium));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Cle de palier invalide dans battlepass.yml : " + key);
            }
        }
        levels.sort(Comparator.comparingInt(BattlePassLevel::level));
        plugin.getLogger().info(levels.size() + " palier(s) de BattlePass charge(s).");
    }

    private BattlePassReward parseReward(ConfigurationSection section) {
        Reward reward = RewardParser.parse(section);
        return reward == null ? null : new BattlePassReward(reward);
    }

    public List<BattlePassLevel> getLevels() {
        return levels;
    }

    public long getXpPerMinute() {
        return xpPerMinute;
    }

    public double getPremiumPrice() {
        return premiumPrice;
    }

    /** Le palier atteint pour une quantite d'xp donnee (le plus haut niveau dont xp-requis <= xp). */
    public int computeLevel(long xp) {
        int current = 0;
        for (BattlePassLevel level : levels) {
            if (xp >= level.xpRequired()) {
                current = level.level();
            }
        }
        return current;
    }

    public BattlePassLevel getLevel(int level) {
        for (BattlePassLevel l : levels) {
            if (l.level() == level) {
                return l;
            }
        }
        return null;
    }

    // ---- Progression joueur ----

    public synchronized long getXp(UUID uuid) {
        String select = "SELECT xp FROM battlepass_joueurs WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("xp");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture xp battlepass pour " + uuid + " : " + e.getMessage());
        }
        return 0L;
    }

    public synchronized boolean isPremium(UUID uuid) {
        String select = "SELECT premium FROM battlepass_joueurs WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("premium") != 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture statut premium pour " + uuid + " : " + e.getMessage());
        }
        return false;
    }

    /** Ajoute de l'xp au joueur (creant son enregistrement si besoin) et renvoie la nouvelle xp totale. */
    public synchronized long addXp(UUID uuid, long amount) {
        long newXp = Math.max(0, getXp(uuid) + amount);
        String upsert = "INSERT INTO battlepass_joueurs (uuid, xp, premium) VALUES (?, ?, 0) " +
                "ON CONFLICT(uuid) DO UPDATE SET xp = excluded.xp;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, newXp);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour xp battlepass pour " + uuid + " : " + e.getMessage());
        }
        return newXp;
    }

    public synchronized void setPremium(UUID uuid, boolean premium) {
        String upsert = "INSERT INTO battlepass_joueurs (uuid, xp, premium) VALUES (?, 0, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET premium = excluded.premium;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, premium ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour statut premium pour " + uuid + " : " + e.getMessage());
        }
    }

    public synchronized boolean hasClaimed(UUID uuid, int level, String track) {
        String select = "SELECT 1 FROM battlepass_reclamations WHERE uuid = ? AND niveau = ? AND piste = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, level);
            statement.setString(3, track);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur verification reclamation battlepass : " + e.getMessage());
            return false;
        }
    }

    public synchronized void markClaimed(UUID uuid, int level, String track) {
        String insert = "INSERT OR IGNORE INTO battlepass_reclamations (uuid, niveau, piste) VALUES (?, ?, ?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, level);
            statement.setString(3, track);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement reclamation battlepass : " + e.getMessage());
        }
    }

    /** Charge en un seul passage l'ensemble des reclamations d'un joueur (uuid#niveau#piste). A appeler hors thread principal. */
    public synchronized Set<String> getAllClaims(UUID uuid) {
        Set<String> claims = new HashSet<>();
        String select = "SELECT niveau, piste FROM battlepass_reclamations WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    claims.add(rs.getInt("niveau") + "#" + rs.getString("piste"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture reclamations battlepass pour " + uuid + " : " + e.getMessage());
        }
        return claims;
    }
}
