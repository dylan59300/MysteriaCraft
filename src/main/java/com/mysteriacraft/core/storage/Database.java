package com.mysteriacraft.core.storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gere la connexion SQLite unique du plugin.
 * SQLite ne supporte pas bien les acces concurrents multiples : toutes les
 * requetes doivent passer par une methode synchronized ou etre executees
 * de maniere sequentielle (voir les Managers qui utilisent cette classe).
 */
public class Database {

    private final Plugin plugin;
    private final File dbFile;
    private Connection connection;

    public Database(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), fileName);
    }

    public void connect() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL;");
                statement.execute("PRAGMA foreign_keys=ON;");
            }

            plugin.getLogger().info("Connexion a la base de donnees SQLite etablie (" + dbFile.getName() + ").");
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().severe("Impossible de se connecter a la base de donnees SQLite : " + e.getMessage());
        }
    }

    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la verification de la connexion SQLite : " + e.getMessage());
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Connexion a la base de donnees fermee.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la fermeture de la base de donnees : " + e.getMessage());
        }
    }
}
