package com.mysteriacraft.rank;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Systeme de prestige : un joueur peut "prester" en depensant de l'argent pour monter d'un
 * palier permanent (voir ranks.yml), qui accorde un bonus cumulatif (ex: +% sur les ventes de la
 * Boutique, voir ShopService#sell). Le niveau est persiste en SQLite et mis en cache memoire.
 */
public class RankManager {

    /** Un palier de prestige : cout pour l'atteindre depuis le palier precedent, et bonus accorde
     * (remplace le bonus du palier precedent, ne s'additionne pas dessus : le bonus configure est
     * deja la valeur totale a ce palier). */
    public record PrestigeTier(int niveau, String nom, double cout, double bonusVentePourcent) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager ranksConfig;
    private final List<PrestigeTier> tiers = new ArrayList<>();
    private final Map<UUID, Integer> cache = new ConcurrentHashMap<>();

    public RankManager(Plugin plugin, Database database, ConfigManager ranksConfig) {
        this.plugin = plugin;
        this.database = database;
        this.ranksConfig = ranksConfig;
        createTable();
        loadTiers();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS prestige (uuid TEXT PRIMARY KEY, niveau INTEGER NOT NULL DEFAULT 0);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'prestige' : " + e.getMessage());
        }
    }

    public void loadTiers() {
        tiers.clear();
        for (Map<?, ?> raw : ranksConfig.get().getMapList("paliers")) {
            Object niveauRaw = raw.get("niveau");
            if (niveauRaw == null) {
                continue;
            }
            int niveau = Integer.parseInt(String.valueOf(niveauRaw));
            String nom = raw.containsKey("nom") ? String.valueOf(raw.get("nom")) : "Prestige " + niveau;
            double cout = raw.containsKey("cout") ? Double.parseDouble(String.valueOf(raw.get("cout"))) : 0;
            double bonus = raw.containsKey("bonus-vente-pourcent") ? Double.parseDouble(String.valueOf(raw.get("bonus-vente-pourcent"))) : 0;
            tiers.add(new PrestigeTier(niveau, nom, cout, bonus));
        }
        tiers.sort(Comparator.comparingInt(PrestigeTier::niveau));
        plugin.getLogger().info(tiers.size() + " palier(s) de prestige charge(s).");
    }

    /** Niveau de prestige actuel (0 = aucun). Requete synchrone : a appeler hors du thread principal
     * si non deja en cache. */
    public int getNiveau(UUID uuid) {
        Integer cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        String select = "SELECT niveau FROM prestige WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                int niveau = rs.next() ? rs.getInt("niveau") : 0;
                cache.put(uuid, niveau);
                return niveau;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture prestige pour " + uuid + " : " + e.getMessage());
            return 0;
        }
    }

    public PrestigeTier getCurrentTier(UUID uuid) {
        int niveau = getNiveau(uuid);
        for (PrestigeTier tier : tiers) {
            if (tier.niveau() == niveau) {
                return tier;
            }
        }
        return null;
    }

    /** Prochain palier accessible, ou null si le palier maximum est deja atteint. */
    public PrestigeTier getNextTier(UUID uuid) {
        int niveau = getNiveau(uuid);
        for (PrestigeTier tier : tiers) {
            if (tier.niveau() == niveau + 1) {
                return tier;
            }
        }
        return null;
    }

    /** Bonus de vente (Boutique) actuellement accorde par le prestige, en % (0 si aucun palier). */
    public double getBonusVentePourcent(UUID uuid) {
        PrestigeTier tier = getCurrentTier(uuid);
        return tier == null ? 0 : tier.bonusVentePourcent();
    }

    public void setNiveau(UUID uuid, int niveau) {
        cache.put(uuid, niveau);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String upsert = "INSERT INTO prestige (uuid, niveau) VALUES (?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET niveau = excluded.niveau;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, niveau);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur sauvegarde prestige pour " + uuid + " : " + e.getMessage());
            }
        });
    }
}
