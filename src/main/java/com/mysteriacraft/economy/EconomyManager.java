package com.mysteriacraft.economy;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gere l'economie interne du serveur (aucune dependance a Vault ou tout autre plugin).
 * Les soldes sont mis en cache memoire pour les joueurs connectes, et persistes en SQLite.
 *
 * IMPORTANT : Database#getConnection() renvoie la connexion SQLite unique et partagee du plugin.
 * Elle ne doit JAMAIS etre fermee ici (pas de try-with-resources dessus) : seuls les
 * PreparedStatement/ResultSet sont fermes localement.
 */
public class EconomyManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager config;
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

    private final ConcurrentHashMap<UUID, Double> cache = new ConcurrentHashMap<>();

    public EconomyManager(Plugin plugin, Database database, ConfigManager config) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS economie (" +
                "uuid TEXT PRIMARY KEY, " +
                "nom TEXT NOT NULL, " +
                "solde REAL NOT NULL DEFAULT 0" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'economie' : " + e.getMessage());
        }
    }

    /**
     * Charge (ou cree) le compte d'un joueur en base et le place en cache.
     * A appeler de maniere asynchrone (ex: PlayerJoinListener).
     */
    public synchronized void loadAccount(UUID uuid, String name) {
        String select = "SELECT solde FROM economie WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    cache.put(uuid, rs.getDouble("solde"));
                    updateName(uuid, name);
                } else {
                    double start = config.get().getDouble("economie.solde-depart", 0.0);
                    createAccount(uuid, name, start);
                    cache.put(uuid, start);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur chargement compte economie pour " + name + " : " + e.getMessage());
        }
    }

    private void createAccount(UUID uuid, String name, double balance) throws SQLException {
        String insert = "INSERT OR IGNORE INTO economie (uuid, nom, solde) VALUES (?, ?, ?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setDouble(3, balance);
            statement.executeUpdate();
        }
    }

    private void updateName(UUID uuid, String name) throws SQLException {
        String update = "UPDATE economie SET nom = ? WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setString(1, name);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    /** Retire le joueur du cache memoire (a appeler a la deconnexion). */
    public void unloadAccount(UUID uuid) {
        cache.remove(uuid);
    }

    /** Sauvegarde immediate et synchrone du solde en base (utiliser hors du thread principal). */
    private synchronized void persist(UUID uuid, double amount) {
        String update = "UPDATE economie SET solde = ? WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setDouble(1, amount);
            statement.setString(2, uuid.toString());
            int updated = statement.executeUpdate();
            if (updated == 0) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                createAccount(uuid, offlinePlayer.getName() != null ? offlinePlayer.getName() : "inconnu", amount);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur sauvegarde solde pour " + uuid + " : " + e.getMessage());
        }
    }

    private void persistAsync(UUID uuid, double amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> persist(uuid, amount));
    }

    /** Retourne le solde d'un joueur, en interrogeant la base si le joueur n'est pas en cache (hors-ligne). */
    public synchronized double getBalance(UUID uuid) {
        Double cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        String select = "SELECT solde FROM economie WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("solde");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture solde pour " + uuid + " : " + e.getMessage());
        }
        return 0.0;
    }

    public synchronized boolean hasAccount(UUID uuid) {
        if (cache.containsKey(uuid)) {
            return true;
        }
        String select = "SELECT 1 FROM economie WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur verification compte pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    /** Definit le solde d'un joueur (borne entre 0 et solde-max, sauf si le negatif est autorise). */
    public void setBalance(UUID uuid, double amount) {
        double solde = clamp(amount);
        cache.put(uuid, solde);
        persistAsync(uuid, solde);
    }

    public void deposit(UUID uuid, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Le montant a deposer ne peut pas etre negatif.");
        }
        double newBalance = clamp(getBalance(uuid) + amount);
        cache.put(uuid, newBalance);
        persistAsync(uuid, newBalance);
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Le montant a retirer ne peut pas etre negatif.");
        }
        double current = getBalance(uuid);
        boolean allowNegative = config.get().getBoolean("economie.autoriser-solde-negatif", false);
        if (!allowNegative && current < amount) {
            return false;
        }
        double newBalance = clamp(current - amount);
        cache.put(uuid, newBalance);
        persistAsync(uuid, newBalance);
        return true;
    }

    /** Transfert atomique cote cache : verifie les fonds puis effectue le retrait/depot. */
    public boolean transfer(UUID from, UUID to, double amount) {
        if (!withdraw(from, amount)) {
            return false;
        }
        deposit(to, amount);
        return true;
    }

    private double clamp(double amount) {
        double max = config.get().getDouble("economie.solde-max", Double.MAX_VALUE);
        boolean allowNegative = config.get().getBoolean("economie.autoriser-solde-negatif", false);
        double result = Math.min(amount, max);
        if (!allowNegative) {
            result = Math.max(result, 0.0);
        }
        return result;
    }

    public String format(double amount) {
        String symbole = config.get().getString("economie.symbole", "$");
        return decimalFormat.format(amount) + symbole;
    }

    public record TopEntry(String nom, double solde) {}

    /** Classement des soldes, requete effectuee en base (inclut les joueurs hors-ligne). Appeler hors du thread principal. */
    public synchronized List<TopEntry> getTop(int limit, int offset) {
        List<TopEntry> results = new ArrayList<>();
        String select = "SELECT nom, solde FROM economie ORDER BY solde DESC LIMIT ? OFFSET ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(new TopEntry(rs.getString("nom"), rs.getDouble("solde")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture classement economie : " + e.getMessage());
        }
        return results;
    }

    /**
     * Recherche l'UUID d'un joueur connu en base par son pseudo (recherche insensible a la casse).
     * A appeler hors du thread principal. Pour un joueur en ligne, preferez Player#getUniqueId()
     * recupere sur le thread principal avant d'appeler cette methode.
     */
    public synchronized UUID findUuidByName(String name) {
        String select = "SELECT uuid FROM economie WHERE LOWER(nom) = LOWER(?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur recherche UUID pour " + name + " : " + e.getMessage());
        }
        return null;
    }

    public synchronized int countAccounts() {
        String select = "SELECT COUNT(*) AS total FROM economie;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur comptage comptes economie : " + e.getMessage());
        }
        return 0;
    }
}
