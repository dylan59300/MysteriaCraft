package com.mysteriacraft.shop;

import com.mysteriacraft.core.storage.Database;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Stock & reappovisionnement de la Boutique (voir ShopManager.ShopItem#stockMax dans boutique.yml,
 * cle "stock-max" : absente ou 0 = stock illimite, comportement par defaut inchange). Un article a
 * stock limite se reapprovisionne tout seul de "reappro-quantite" toutes les
 * "reappro-intervalle-minutes", calcule PARESSEUSEMENT a chaque consultation/achat (pas de tache
 * planifiee necessaire), plafonne a "stock-max".
 */
public class StockManager {

    private final Plugin plugin;
    private final Database database;

    public StockManager(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS shop_stock (" +
                "categorie TEXT NOT NULL, item TEXT NOT NULL, quantite INTEGER NOT NULL, " +
                "dernier_reappro INTEGER NOT NULL, PRIMARY KEY (categorie, item));";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table shop_stock : " + e.getMessage());
        }
    }

    /** Stock restant de cet article, apres avoir applique le reappro paresseux si necessaire.
     * -1 si l'article n'a pas de stock limite (illimite). */
    public synchronized int getStockRestant(ShopManager.ShopItem item) {
        if (!item.hasStockLimite()) {
            return -1;
        }
        return computeCurrentStock(item);
    }

    /** Tente de consommer 1 unite de stock pour cet achat. Renvoie toujours true pour un article a
     * stock illimite. Renvoie false (sans rien modifier) si le stock est a 0. */
    public synchronized boolean consommerStock(ShopManager.ShopItem item) {
        if (!item.hasStockLimite()) {
            return true;
        }
        int current = computeCurrentStock(item);
        if (current <= 0) {
            return false;
        }
        updateStock(item, current - 1, getDernierReappro(item));
        return true;
    }

    /** Minutes restantes avant le prochain palier de reappro (0 si le stock est deja au maximum). */
    public synchronized long getMinutesAvantProchainReappro(ShopManager.ShopItem item) {
        if (!item.hasStockLimite()) {
            return 0;
        }
        int current = computeCurrentStock(item);
        if (current >= item.stockMax()) {
            return 0;
        }
        long dernierReappro = getDernierReappro(item);
        long prochain = dernierReappro + item.reapproIntervalleMinutes() * 60_000L;
        return Math.max(0, (prochain - System.currentTimeMillis()) / 60_000L);
    }

    private int computeCurrentStock(ShopManager.ShopItem item) {
        String select = "SELECT quantite, dernier_reappro FROM shop_stock WHERE categorie = ? AND item = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, item.categoryId());
            statement.setString(2, item.id());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    long now = System.currentTimeMillis();
                    updateStock(item, item.stockMax(), now);
                    return item.stockMax();
                }
                int quantite = rs.getInt("quantite");
                long dernierReappro = rs.getLong("dernier_reappro");

                long elapsedMs = System.currentTimeMillis() - dernierReappro;
                long intervalMs = item.reapproIntervalleMinutes() * 60_000L;
                long intervalsPassed = elapsedMs / intervalMs;
                if (intervalsPassed <= 0 || quantite >= item.stockMax()) {
                    return quantite;
                }
                int nouvelleQuantite = (int) Math.min(item.stockMax(), quantite + intervalsPassed * item.reapproQuantite());
                long nouveauDernierReappro = dernierReappro + intervalsPassed * intervalMs;
                updateStock(item, nouvelleQuantite, nouveauDernierReappro);
                return nouvelleQuantite;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture stock boutique pour " + item.categoryId() + ":" + item.id() + " : " + e.getMessage());
            return item.stockMax();
        }
    }

    private long getDernierReappro(ShopManager.ShopItem item) {
        String select = "SELECT dernier_reappro FROM shop_stock WHERE categorie = ? AND item = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, item.categoryId());
            statement.setString(2, item.id());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("dernier_reappro") : System.currentTimeMillis();
            }
        } catch (SQLException e) {
            return System.currentTimeMillis();
        }
    }

    private void updateStock(ShopManager.ShopItem item, int quantite, long dernierReappro) {
        String upsert = "INSERT INTO shop_stock (categorie, item, quantite, dernier_reappro) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(categorie, item) DO UPDATE SET quantite = excluded.quantite, dernier_reappro = excluded.dernier_reappro;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, item.categoryId());
            statement.setString(2, item.id());
            statement.setInt(3, quantite);
            statement.setLong(4, dernierReappro);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour stock boutique pour " + item.categoryId() + ":" + item.id() + " : " + e.getMessage());
        }
    }
}
