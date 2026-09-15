package com.mysteriacraft.encheres;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persiste les annonces de l'Hotel des Ventes en SQLite (item serialise en base64 via
 * ItemSerializer, colonne texte comme n'importe quelle autre donnee).
 */
public class EnchereManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager encheresConfig;

    private double commissionPourcent;
    private int maxAnnoncesParJoueur;
    private double prixMinimum;

    public EnchereManager(Plugin plugin, Database database, ConfigManager encheresConfig) {
        this.plugin = plugin;
        this.database = database;
        this.encheresConfig = encheresConfig;
        createTable();
        loadConfig();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS encheres (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "vendeur_uuid TEXT NOT NULL, " +
                "vendeur_nom TEXT NOT NULL, " +
                "item_base64 TEXT NOT NULL, " +
                "prix REAL NOT NULL, " +
                "date_iso TEXT NOT NULL" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'encheres' : " + e.getMessage());
        }
    }

    public void loadConfig() {
        commissionPourcent = Math.max(0, encheresConfig.get().getDouble("commission-pourcent", 5));
        maxAnnoncesParJoueur = Math.max(1, encheresConfig.get().getInt("max-annonces-par-joueur", 5));
        prixMinimum = Math.max(0, encheresConfig.get().getDouble("prix-minimum", 1));
    }

    public double getCommissionPourcent() {
        return commissionPourcent;
    }

    public int getMaxAnnoncesParJoueur() {
        return maxAnnoncesParJoueur;
    }

    public double getPrixMinimum() {
        return prixMinimum;
    }

    public synchronized int countAnnoncesActives(UUID vendeurUuid) {
        String sql = "SELECT COUNT(*) AS total FROM encheres WHERE vendeur_uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vendeurUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur comptage annonces pour " + vendeurUuid + " : " + e.getMessage());
        }
        return 0;
    }

    /** Cree une nouvelle annonce et renvoie son id genere. */
    public synchronized int creerAnnonce(UUID vendeurUuid, String vendeurNom, ItemStack item, double prix) {
        String insert = "INSERT INTO encheres (vendeur_uuid, vendeur_nom, item_base64, prix, date_iso) VALUES (?, ?, ?, ?, ?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, vendeurUuid.toString());
            statement.setString(2, vendeurNom);
            statement.setString(3, ItemSerializer.serialize(item));
            statement.setDouble(4, prix);
            statement.setString(5, LocalDate.now().toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation annonce pour " + vendeurUuid + " : " + e.getMessage());
        }
        return -1;
    }

    private Annonce mapRow(ResultSet rs) throws SQLException {
        return new Annonce(
                rs.getInt("id"),
                UUID.fromString(rs.getString("vendeur_uuid")),
                rs.getString("vendeur_nom"),
                ItemSerializer.deserialize(rs.getString("item_base64")),
                rs.getDouble("prix"),
                rs.getString("date_iso")
        );
    }

    public synchronized List<Annonce> getAnnoncesActives() {
        List<Annonce> annonces = new ArrayList<>();
        String sql = "SELECT * FROM encheres ORDER BY id DESC;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                annonces.add(mapRow(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture annonces actives : " + e.getMessage());
        }
        return annonces;
    }

    public synchronized List<Annonce> getAnnoncesDe(UUID vendeurUuid) {
        List<Annonce> annonces = new ArrayList<>();
        String sql = "SELECT * FROM encheres WHERE vendeur_uuid = ? ORDER BY id DESC;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vendeurUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    annonces.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture annonces pour " + vendeurUuid + " : " + e.getMessage());
        }
        return annonces;
    }

    public synchronized Annonce getAnnonce(int id) {
        String sql = "SELECT * FROM encheres WHERE id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture annonce " + id + " : " + e.getMessage());
        }
        return null;
    }

    /** Supprime une annonce (achat ou annulation), renvoie faux si elle n'existait deja plus
     * (evite un double-achat/double-annulation concurrent). */
    public synchronized boolean supprimerAnnonce(int id) {
        String delete = "DELETE FROM encheres WHERE id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(delete)) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur suppression annonce " + id + " : " + e.getMessage());
            return false;
        }
    }
}
