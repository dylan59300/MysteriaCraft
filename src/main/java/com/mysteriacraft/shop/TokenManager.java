package com.mysteriacraft.shop;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

/**
 * Jetons Boutique : monnaie secondaire independante de l'economie principale, gagnee notamment
 * via le cashback (voir ShopService#buy) et convertible dans les deux sens avec l'argent, a un
 * taux qui varie chaque jour (tire deterministement, identique pour tout le monde jusqu'au
 * lendemain) dans une fourchette configurable.
 */
public class TokenManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager promotionsConfig;

    public TokenManager(Plugin plugin, Database database, ConfigManager promotionsConfig) {
        this.plugin = plugin;
        this.database = database;
        this.promotionsConfig = promotionsConfig;
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS boutique_jetons (uuid TEXT PRIMARY KEY, jetons INTEGER NOT NULL DEFAULT 0);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'boutique_jetons' : " + e.getMessage());
        }
    }

    /** Requete synchrone : a appeler hors du thread principal. */
    public long getJetons(UUID uuid) {
        String select = "SELECT jetons FROM boutique_jetons WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("jetons") : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture jetons pour " + uuid + " : " + e.getMessage());
            return 0;
        }
    }

    private void setJetons(UUID uuid, long montant) {
        String upsert = "INSERT INTO boutique_jetons (uuid, jetons) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET jetons = excluded.jetons;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, Math.max(0, montant));
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur sauvegarde jetons pour " + uuid + " : " + e.getMessage());
        }
    }

    public void addJetons(UUID uuid, long montant) {
        setJetons(uuid, getJetons(uuid) + montant);
    }

    /** Retire des jetons si le solde est suffisant. Renvoie false sinon (rien n'est retire). */
    public boolean removeJetons(UUID uuid, long montant) {
        long solde = getJetons(uuid);
        if (solde < montant) {
            return false;
        }
        setJetons(uuid, solde - montant);
        return true;
    }

    /** Taux d'argent necessaire pour 1 jeton aujourd'hui (varie chaque jour, meme pour tous). */
    public double getTauxArgentParJeton() {
        double min = promotionsConfig.get().getDouble("jetons.taux-argent-par-jeton-min", 80);
        double max = promotionsConfig.get().getDouble("jetons.taux-argent-par-jeton-max", 120);
        Random random = new Random(LocalDate.now().toEpochDay() * 17);
        return min + random.nextDouble() * (max - min);
    }

    public String formatJetons(long montant) {
        return montant + " jeton(s)";
    }

    /** Jetons de cashback accordes pour un achat de ce montant (voir ShopService#buy). */
    public long calculerCashback(double montantDepense) {
        double parCent = promotionsConfig.get().getDouble("jetons.cashback-jeton-par-100-argent", 1);
        return (long) Math.floor(montantDepense / 100.0 * parCent);
    }
}
