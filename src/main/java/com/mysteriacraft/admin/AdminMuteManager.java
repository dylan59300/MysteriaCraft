package com.mysteriacraft.admin;

import com.mysteriacraft.core.storage.Database;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Mute (voir /admin, sanctions rapides) : coupe le chat d'un joueur jusqu'a expiration (ou pour
 * toujours si "permanent"). Independant de tout plugin de permissions externe.
 */
public class AdminMuteManager {

    /** Utilise comme date d'expiration pour un mute permanent. */
    public static final long PERMANENT = Long.MAX_VALUE;

    private final Plugin plugin;
    private final Database database;

    public AdminMuteManager(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS admin_mutes (" +
                "uuid TEXT PRIMARY KEY, expire_at INTEGER NOT NULL, raison TEXT NOT NULL);";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table admin_mutes : " + e.getMessage());
        }
    }

    public void mute(UUID uuid, long expireAtMillis, String raison) {
        String upsert = "INSERT INTO admin_mutes (uuid, expire_at, raison) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET expire_at = excluded.expire_at, raison = excluded.raison;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, expireAtMillis);
            statement.setString(3, raison);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement mute pour " + uuid + " : " + e.getMessage());
        }
    }

    public void unmute(UUID uuid) {
        String delete = "DELETE FROM admin_mutes WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(delete)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur suppression mute pour " + uuid + " : " + e.getMessage());
        }
    }

    public record MuteInfo(long expireAtMillis, String raison) {
    }

    /** null si non mute (ou mute expire, auto-nettoye a la lecture). */
    public MuteInfo getMute(UUID uuid) {
        String select = "SELECT expire_at, raison FROM admin_mutes WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long expireAt = rs.getLong("expire_at");
                if (expireAt <= System.currentTimeMillis()) {
                    unmute(uuid);
                    return null;
                }
                return new MuteInfo(expireAt, rs.getString("raison"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture mute pour " + uuid + " : " + e.getMessage());
            return null;
        }
    }

    public boolean isMuted(UUID uuid) {
        return getMute(uuid) != null;
    }
}
