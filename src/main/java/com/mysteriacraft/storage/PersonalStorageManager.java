package com.mysteriacraft.storage;

import com.mysteriacraft.core.storage.Database;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Persiste un espace de stockage personnel generique par joueur ET par "type" (ex: "sac",
 * "coffrefort"), afin de partager la meme logique de serialisation/BDD entre plusieurs modules
 * de stockage. Une ligne = un joueur + un type de stockage : sa taille actuelle (en slots) et le
 * contenu serialise (comme une sauvegarde d'ItemStack[] classique).
 *
 * Toutes les methodes font des appels SQL synchrones : a appeler hors du thread principal.
 */
public class PersonalStorageManager {

    private final Plugin plugin;
    private final Database database;

    public PersonalStorageManager(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS personal_storage (" +
                "uuid TEXT NOT NULL, " +
                "type TEXT NOT NULL, " +
                "taille INTEGER NOT NULL, " +
                "contenu BLOB, " +
                "PRIMARY KEY (uuid, type)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'personal_storage' : " + e.getMessage());
        }
    }

    /** Taille actuelle (en slots) de ce stockage, ou defaultSize si jamais initialise. */
    public int getSize(UUID uuid, String type, int defaultSize) {
        String select = "SELECT taille FROM personal_storage WHERE uuid = ? AND type = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, type);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("taille") : defaultSize;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture taille stockage '" + type + "' pour " + uuid + " : " + e.getMessage());
            return defaultSize;
        }
    }

    /** Fixe une nouvelle taille (le contenu existant est conserve, tronque si la nouvelle taille
     * est plus petite : ne devrait jamais arriver en pratique puisque les ameliorations n'ajoutent
     * que des slots). */
    public void setSize(UUID uuid, String type, int size) {
        ItemStack[] current = loadContents(uuid, type, size);
        saveContents(uuid, type, size, current);
    }

    /** Charge le contenu, redimensionne au besoin (nouveaux slots vides si agrandi). */
    public ItemStack[] loadContents(UUID uuid, String type, int size) {
        String select = "SELECT contenu FROM personal_storage WHERE uuid = ? AND type = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, type);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return new ItemStack[size];
                }
                byte[] bytes = rs.getBytes("contenu");
                if (bytes == null) {
                    return new ItemStack[size];
                }
                return deserialize(bytes, size);
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            plugin.getLogger().severe("Erreur lecture contenu stockage '" + type + "' pour " + uuid + " : " + e.getMessage());
            return new ItemStack[size];
        }
    }

    public void saveContents(UUID uuid, String type, int size, ItemStack[] contents) {
        try {
            byte[] bytes = serialize(contents);
            String upsert = "INSERT INTO personal_storage (uuid, type, taille, contenu) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(uuid, type) DO UPDATE SET taille = excluded.taille, contenu = excluded.contenu;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, type);
                statement.setInt(3, size);
                statement.setBytes(4, bytes);
                statement.executeUpdate();
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().severe("Erreur sauvegarde contenu stockage '" + type + "' pour " + uuid + " : " + e.getMessage());
        }
    }

    private byte[] serialize(ItemStack[] items) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(out)) {
            data.writeInt(items.length);
            for (ItemStack item : items) {
                data.writeObject(item);
            }
            return out.toByteArray();
        }
    }

    private ItemStack[] deserialize(byte[] bytes, int size) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream data = new BukkitObjectInputStream(in)) {
            int storedLength = data.readInt();
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < storedLength; i++) {
                ItemStack item = (ItemStack) data.readObject();
                if (i < size) {
                    items[i] = item;
                }
            }
            return items;
        }
    }
}
