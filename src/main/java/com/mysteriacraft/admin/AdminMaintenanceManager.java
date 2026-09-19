package com.mysteriacraft.admin;

import com.mysteriacraft.core.storage.Database;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Mode maintenance par module (voir /admin, bouton "Basculer maintenance") : bloque l'usage d'une
 * commande joueur precise pour tout le monde sauf les admins (voir AdminMaintenanceListener),
 * sans toucher au code du module concerne.
 */
public class AdminMaintenanceManager {

    private final Plugin plugin;
    private final Database database;
    private final Set<String> commandesDesactivees = new HashSet<>();

    public AdminMaintenanceManager(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        createTable();
        loadFromDatabase();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS admin_maintenance (commande TEXT PRIMARY KEY);";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table admin_maintenance : " + e.getMessage());
        }
    }

    private void loadFromDatabase() {
        String select = "SELECT commande FROM admin_maintenance;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                commandesDesactivees.add(rs.getString("commande").toLowerCase());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture admin_maintenance : " + e.getMessage());
        }
    }

    public boolean isDisabled(String commande) {
        return commandesDesactivees.contains(commande.toLowerCase());
    }

    /** Bascule l'etat de maintenance de cette commande, et renvoie le nouvel etat (true = desormais desactivee). */
    public boolean toggle(String commande) {
        String key = commande.toLowerCase();
        Connection connection = database.getConnection();
        if (commandesDesactivees.remove(key)) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM admin_maintenance WHERE commande = ?;")) {
                statement.setString(1, key);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur suppression admin_maintenance pour " + key + " : " + e.getMessage());
            }
            return false;
        }
        commandesDesactivees.add(key);
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO admin_maintenance (commande) VALUES (?);")) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement admin_maintenance pour " + key + " : " + e.getMessage());
        }
        return true;
    }
}
