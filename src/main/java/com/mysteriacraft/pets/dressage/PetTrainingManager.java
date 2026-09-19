package com.mysteriacraft.pets.dressage;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Dressage du pet actif : chaque kill (voir PetTrainingListener) donne de l'xp au joueur qui a un
 * pet actif, faisant monter son "niveau de dressage" (independant du pet choisi, conserve meme en
 * changeant de pet). Chaque niveau accorde un bonus permanent de degats et d'esquive, ajoute a
 * celui du pet actif (voir PetService#applyDegatsBonus / #getEsquivePourcent).
 */
public class PetTrainingManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager dressageConfig;

    public PetTrainingManager(Plugin plugin, Database database, ConfigManager dressageConfig) {
        this.plugin = plugin;
        this.database = database;
        this.dressageConfig = dressageConfig;
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS pet_dressage (uuid TEXT PRIMARY KEY, xp INTEGER NOT NULL DEFAULT 0);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'pet_dressage' : " + e.getMessage());
        }
    }

    public long getXpParKill() {
        return dressageConfig.get().getLong("xp-par-kill", 10);
    }

    private long getXpParNiveau() {
        return Math.max(1, dressageConfig.get().getLong("xp-par-niveau", 100));
    }

    private int getNiveauMax() {
        return Math.max(1, dressageConfig.get().getInt("niveau-max", 20));
    }

    private double getBonusDegatsParNiveau() {
        return dressageConfig.get().getDouble("bonus-degats-par-niveau", 0.2);
    }

    private double getBonusEsquiveParNiveau() {
        return dressageConfig.get().getDouble("bonus-esquive-par-niveau", 0.5);
    }

    /** Requete synchrone : a appeler hors du thread principal. */
    public long getXp(UUID uuid) {
        String select = "SELECT xp FROM pet_dressage WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("xp") : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture xp de dressage pour " + uuid + " : " + e.getMessage());
            return 0;
        }
    }

    public int getNiveau(UUID uuid) {
        return (int) Math.min(getNiveauMax(), getXp(uuid) / getXpParNiveau());
    }

    public double getBonusDegats(UUID uuid) {
        return getNiveau(uuid) * getBonusDegatsParNiveau();
    }

    public double getBonusEsquive(UUID uuid) {
        return getNiveau(uuid) * getBonusEsquiveParNiveau();
    }

    /** Ajoute de l'xp et persiste. Requete synchrone : a appeler hors du thread principal. Renvoie
     * true si ce gain d'xp a fait passer un niveau (pour notifier le joueur). */
    public boolean addXp(UUID uuid, long amount) {
        int niveauAvant = getNiveau(uuid);
        long xpActuelle = getXp(uuid);
        long nouvelleXp = xpActuelle + amount;

        String upsert = "INSERT INTO pet_dressage (uuid, xp) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET xp = excluded.xp;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, nouvelleXp);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur sauvegarde xp de dressage pour " + uuid + " : " + e.getMessage());
        }

        int niveauApres = (int) Math.min(getNiveauMax(), nouvelleXp / getXpParNiveau());
        return niveauApres > niveauAvant;
    }
}
