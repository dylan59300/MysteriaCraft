package com.mysteriacraft.homes;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import com.mysteriacraft.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gere les homes multiples (/home) : chaque joueur peut en sauvegarder plusieurs, nommes,
 * jusqu'a une limite = base (homes.yml) + bonus par niveau de prestige (voir RankManager).
 * Toutes les methodes de lecture/ecriture SQL sont synchrones : a appeler hors du thread principal.
 */
public class HomeManager {

    public record Home(String nom, Location location) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager homesConfig;
    private final RankManager rankManager;

    public HomeManager(Plugin plugin, Database database, ConfigManager homesConfig, RankManager rankManager) {
        this.plugin = plugin;
        this.database = database;
        this.homesConfig = homesConfig;
        this.rankManager = rankManager;
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS homes (" +
                "uuid TEXT NOT NULL, " +
                "nom TEXT NOT NULL, " +
                "monde TEXT NOT NULL, " +
                "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, " +
                "yaw REAL NOT NULL, pitch REAL NOT NULL, " +
                "PRIMARY KEY (uuid, nom)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'homes' : " + e.getMessage());
        }
    }

    public List<Home> getHomes(UUID uuid) {
        List<Home> homes = new ArrayList<>();
        String select = "SELECT nom, monde, x, y, z, yaw, pitch FROM homes WHERE uuid = ? ORDER BY nom;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString("monde"));
                    if (world == null) {
                        continue;
                    }
                    Location location = new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch"));
                    homes.add(new Home(rs.getString("nom"), location));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture homes pour " + uuid + " : " + e.getMessage());
        }
        return homes;
    }

    public Location getHome(UUID uuid, String nom) {
        String select = "SELECT monde, x, y, z, yaw, pitch FROM homes WHERE uuid = ? AND nom = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, nom.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                World world = Bukkit.getWorld(rs.getString("monde"));
                if (world == null) {
                    return null;
                }
                return new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture home '" + nom + "' pour " + uuid + " : " + e.getMessage());
            return null;
        }
    }

    public int countHomes(UUID uuid) {
        String select = "SELECT COUNT(*) AS total FROM homes WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur comptage homes pour " + uuid + " : " + e.getMessage());
            return 0;
        }
    }

    /** Limite de homes = base (homes.yml) + bonus-par-niveau-prestige * niveau de prestige actuel. */
    public int getLimit(UUID uuid) {
        int base = homesConfig.get().getInt("limite-homes-defaut", 3);
        int bonusParNiveau = homesConfig.get().getInt("bonus-par-niveau-prestige", 1);
        return base + bonusParNiveau * rankManager.getNiveau(uuid);
    }

    public boolean exists(UUID uuid, String nom) {
        return getHome(uuid, nom) != null;
    }

    public void setHome(UUID uuid, String nom, Location location) {
        String upsert = "INSERT INTO homes (uuid, nom, monde, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid, nom) DO UPDATE SET monde = excluded.monde, x = excluded.x, y = excluded.y, " +
                "z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, nom.toLowerCase());
            statement.setString(3, location.getWorld().getName());
            statement.setDouble(4, location.getX());
            statement.setDouble(5, location.getY());
            statement.setDouble(6, location.getZ());
            statement.setFloat(7, location.getYaw());
            statement.setFloat(8, location.getPitch());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur sauvegarde home '" + nom + "' pour " + uuid + " : " + e.getMessage());
        }
    }

    public boolean deleteHome(UUID uuid, String nom) {
        String delete = "DELETE FROM homes WHERE uuid = ? AND nom = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(delete)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, nom.toLowerCase());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur suppression home '" + nom + "' pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }
}
