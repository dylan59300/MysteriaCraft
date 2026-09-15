package com.mysteriacraft.banque;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Gere le solde en banque de chaque joueur (SEPARE du solde principal, module Economie) et ses
 * interets composes quotidiens, persistes en SQLite. Les interets sont appliques PARESSEUSEMENT :
 * a chaque lecture/operation, tous les jours ecoules depuis la derniere visite sont appliques
 * d'un coup via la formule composee, sans tache planifiee ni boucle par jour.
 */
public class BanqueManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager banqueConfig;

    private double tauxInteretJour;
    private int interetJoursMax;
    private double plafondSolde;
    private double depotMinimum;

    public BanqueManager(Plugin plugin, Database database, ConfigManager banqueConfig) {
        this.plugin = plugin;
        this.database = database;
        this.banqueConfig = banqueConfig;
        createTable();
        loadConfig();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS banque_comptes (" +
                "uuid TEXT NOT NULL PRIMARY KEY, " +
                "solde REAL NOT NULL DEFAULT 0, " +
                "dernier_jour TEXT NOT NULL" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'banque_comptes' : " + e.getMessage());
        }
    }

    public void loadConfig() {
        tauxInteretJour = banqueConfig.get().getDouble("taux-interet-jour", 0.5);
        interetJoursMax = Math.max(0, banqueConfig.get().getInt("interet-jours-max", 14));
        plafondSolde = banqueConfig.get().getDouble("plafond-solde", 100000);
        depotMinimum = Math.max(0, banqueConfig.get().getDouble("depot-minimum", 0));
    }

    public double getPlafondSolde() {
        return plafondSolde;
    }

    public double getDepotMinimum() {
        return depotMinimum;
    }

    public double getTauxInteretJour() {
        return tauxInteretJour;
    }

    private record Compte(double solde, LocalDate dernierJour) {
    }

    private synchronized Compte lireCompte(UUID uuid) {
        String select = "SELECT solde, dernier_jour FROM banque_comptes WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new Compte(rs.getDouble("solde"), LocalDate.parse(rs.getString("dernier_jour")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture compte banque pour " + uuid + " : " + e.getMessage());
        }
        return new Compte(0, LocalDate.now());
    }

    private synchronized void ecrireCompte(UUID uuid, double solde, LocalDate jour) {
        String upsert = "INSERT INTO banque_comptes (uuid, solde, dernier_jour) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET solde = excluded.solde, dernier_jour = excluded.dernier_jour;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setDouble(2, solde);
            statement.setString(3, jour.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour compte banque pour " + uuid + " : " + e.getMessage());
        }
    }

    /** Applique les interets composes de tous les jours ecoules depuis la derniere visite (plafonne
     * a interet-jours-max), persiste le resultat, et renvoie le solde a jour ET les interets gagnes. */
    public record ResultatInterets(double nouveauSolde, double interetsGagnes) {
    }

    public synchronized ResultatInterets appliquerInteretsEnAttente(UUID uuid) {
        Compte compte = lireCompte(uuid);
        LocalDate today = LocalDate.now();
        long joursEcoules = Math.min(interetJoursMax, java.time.temporal.ChronoUnit.DAYS.between(compte.dernierJour(), today));

        double nouveauSolde = compte.solde();
        double interetsGagnes = 0;
        if (joursEcoules > 0 && compte.solde() > 0 && tauxInteretJour > 0) {
            double soldeApresInterets = compte.solde() * Math.pow(1 + tauxInteretJour / 100.0, joursEcoules);
            soldeApresInterets = Math.min(plafondSolde, soldeApresInterets);
            interetsGagnes = soldeApresInterets - compte.solde();
            nouveauSolde = soldeApresInterets;
        }
        if (joursEcoules > 0) {
            ecrireCompte(uuid, nouveauSolde, today);
        }
        return new ResultatInterets(nouveauSolde, interetsGagnes);
    }

    public synchronized double getSolde(UUID uuid) {
        return appliquerInteretsEnAttente(uuid).nouveauSolde();
    }

    /** Ajoute au solde en banque (deja plafonne a plafond-solde), APRES avoir applique les
     * interets en attente. Renvoie le nouveau solde. */
    public synchronized double deposer(UUID uuid, double montant) {
        double soldeActuel = appliquerInteretsEnAttente(uuid).nouveauSolde();
        double nouveauSolde = Math.min(plafondSolde, soldeActuel + montant);
        ecrireCompte(uuid, nouveauSolde, LocalDate.now());
        return nouveauSolde;
    }

    /** Retire du solde en banque (deja applique les interets en attente). Renvoie -1 si fonds
     * insuffisants (rien n'est modifie dans ce cas), sinon le nouveau solde. */
    public synchronized double retirer(UUID uuid, double montant) {
        double soldeActuel = appliquerInteretsEnAttente(uuid).nouveauSolde();
        if (soldeActuel < montant) {
            return -1;
        }
        double nouveauSolde = soldeActuel - montant;
        ecrireCompte(uuid, nouveauSolde, LocalDate.now());
        return nouveauSolde;
    }
}
