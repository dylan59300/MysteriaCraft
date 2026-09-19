package com.mysteriacraft.shop;

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
import java.util.List;
import java.util.UUID;

/**
 * Fidelite Boutique (voir "idee : niveaux/badges/quetes/coffre-cadeau") : chaque achat rapporte des
 * points de fidelite CUMULATIFS (jamais depenses, contrairement aux Jetons Boutique), qui debloquent
 * des niveaux avec une reduction PERMANENTE (cumulee aux autres reductions, voir PromotionManager)
 * et un coffre-cadeau donne automatiquement des l'atteinte du niveau. Voir /boutique fidelite.
 */
public class LoyaltyManager {

    public record LoyaltyTier(int niveau, int pointsRequis, String nom, double reductionPourcent, Reward cadeau) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager fideliteConfig;

    private final List<LoyaltyTier> tiers = new ArrayList<>();
    private int argentParPoint = 100;

    public LoyaltyManager(Plugin plugin, Database database, ConfigManager fideliteConfig) {
        this.plugin = plugin;
        this.database = database;
        this.fideliteConfig = fideliteConfig;
        createTables();
        loadConfig();
    }

    private void createTables() {
        String[] statements = {
                "CREATE TABLE IF NOT EXISTS shop_fidelite (uuid TEXT PRIMARY KEY, points INTEGER NOT NULL DEFAULT 0);",
                "CREATE TABLE IF NOT EXISTS shop_fidelite_cadeaux (uuid TEXT NOT NULL, niveau INTEGER NOT NULL, " +
                        "PRIMARY KEY (uuid, niveau));"
        };
        Connection connection = database.getConnection();
        for (String sql : statements) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur creation table fidelite boutique : " + e.getMessage());
            }
        }
    }

    public void loadConfig() {
        tiers.clear();
        argentParPoint = Math.max(1, fideliteConfig.get().getInt("argent-par-point", 100));

        ConfigurationSection root = fideliteConfig.get().getConfigurationSection("niveaux");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                int niveau = Integer.parseInt(key);
                int pointsRequis = section.getInt("points-requis", 0);
                String nom = section.getString("nom", "Niveau " + niveau);
                double reduction = section.getDouble("reduction-pourcent", 0);
                Reward cadeau = RewardParser.parse(section.getConfigurationSection("cadeau"));
                tiers.add(new LoyaltyTier(niveau, pointsRequis, nom, reduction, cadeau));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Cle de niveau de fidelite invalide dans fidelite.yml : " + key);
            }
        }
        tiers.sort(Comparator.comparingInt(LoyaltyTier::niveau));
    }

    public List<LoyaltyTier> getTiers() {
        return tiers;
    }

    public int getPoints(UUID uuid) {
        String select = "SELECT points FROM shop_fidelite WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("points");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture points de fidelite pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    /** A appeler apres un achat REELLEMENT paye (montant deja reduit) : convertit l'argent depense
     * en points de fidelite (voir "argent-par-point") et les ajoute. Renvoie le nouveau total. */
    public int addPointsForPurchase(UUID uuid, double montantDepense) {
        int pointsGagnes = (int) Math.floor(montantDepense / argentParPoint);
        if (pointsGagnes <= 0) {
            return getPoints(uuid);
        }
        int nouveauTotal = getPoints(uuid) + pointsGagnes;
        String upsert = "INSERT INTO shop_fidelite (uuid, points) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET points = excluded.points;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, nouveauTotal);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour points de fidelite pour " + uuid + " : " + e.getMessage());
        }
        return nouveauTotal;
    }

    /** Le plus haut niveau atteint pour cette quantite de points, ou null si aucun. */
    public LoyaltyTier getTierForPoints(int points) {
        LoyaltyTier current = null;
        for (LoyaltyTier tier : tiers) {
            if (points >= tier.pointsRequis()) {
                current = tier;
            }
        }
        return current;
    }

    /** Le prochain niveau non encore atteint pour cette quantite de points, ou null si le dernier
     * niveau est deja atteint. */
    public LoyaltyTier getNextTier(int points) {
        for (LoyaltyTier tier : tiers) {
            if (points < tier.pointsRequis()) {
                return tier;
            }
        }
        return null;
    }

    /** Reduction PERMANENTE (en %) liee au niveau de fidelite actuel de ce joueur, 0 si aucun
     * niveau atteint. Cumulee aux autres reductions automatiques, voir PromotionManager. */
    public double getReductionPourcent(UUID uuid) {
        LoyaltyTier tier = getTierForPoints(getPoints(uuid));
        return tier != null ? tier.reductionPourcent() : 0;
    }

    public boolean hasClaimedGift(UUID uuid, int niveau) {
        String select = "SELECT 1 FROM shop_fidelite_cadeaux WHERE uuid = ? AND niveau = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, niveau);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur verification cadeau de fidelite pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    public void markGiftClaimed(UUID uuid, int niveau) {
        String insert = "INSERT OR IGNORE INTO shop_fidelite_cadeaux (uuid, niveau) VALUES (?, ?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, niveau);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement cadeau de fidelite pour " + uuid + " : " + e.getMessage());
        }
    }
}
