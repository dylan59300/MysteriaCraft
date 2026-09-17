package com.mysteriacraft.shop;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Moteur de reductions de la Boutique (voir promotions.yml) : agrege plusieurs sources de
 * reduction AUTOMATIQUES (happy hour, article du jour, premier achat, anniversaire d'inscription,
 * rang VIP) en un pourcentage total, plus les codes promo (actives explicitement via
 * /boutique code). Toutes les reductions se CUMULENT (addition des pourcentages), plafonnees a
 * reduction-max-pourcent pour ne jamais rendre un article gratuit ou negatif.
 *
 * Happy hour et article du jour sont deterministes (seed = jour courant) : identiques pour tous
 * les joueurs et stables toute la journee, sans tache planifiee necessaire (calcules a la demande).
 */
public class PromotionManager {

    public record PromoCode(String code, double reductionPourcent, int usagesMaxParJoueur) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager promotionsConfig;
    private final LoyaltyManager loyaltyManager;
    private final Map<String, PromoCode> codes = new LinkedHashMap<>();
    private final Map<UUID, String> codeActifParJoueur = new LinkedHashMap<>();

    public PromotionManager(Plugin plugin, Database database, ConfigManager promotionsConfig, LoyaltyManager loyaltyManager) {
        this.plugin = plugin;
        this.database = database;
        this.promotionsConfig = promotionsConfig;
        this.loyaltyManager = loyaltyManager;
        createTables();
        loadCodes();
    }

    private void createTables() {
        String premierAchat = "CREATE TABLE IF NOT EXISTS boutique_premier_achat (uuid TEXT PRIMARY KEY);";
        String codesUtilises = "CREATE TABLE IF NOT EXISTS boutique_codes_utilises (" +
                "uuid TEXT NOT NULL, code TEXT NOT NULL, utilisations INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, code));";
        Connection connection = database.getConnection();
        try (PreparedStatement s1 = connection.prepareStatement(premierAchat);
             PreparedStatement s2 = connection.prepareStatement(codesUtilises)) {
            s1.executeUpdate();
            s2.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation tables de promotions : " + e.getMessage());
        }
    }

    public void loadCodes() {
        codes.clear();
        ConfigurationSection root = promotionsConfig.get().getConfigurationSection("codes-promo");
        if (root != null) {
            for (String code : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(code);
                if (section == null) {
                    continue;
                }
                double reduction = section.getDouble("reduction-pourcent", 10);
                int usagesMax = Math.max(1, section.getInt("usages-max-par-joueur", 1));
                codes.put(code.toUpperCase(), new PromoCode(code.toUpperCase(), reduction, usagesMax));
            }
        }
        plugin.getLogger().info(codes.size() + " code(s) promo charge(s).");
    }

    private double get(String path, double def) {
        return promotionsConfig.get().getDouble(path, def);
    }

    private long seedDuJour() {
        return LocalDate.now().toEpochDay();
    }

    // ---- Happy Hour ----

    /** Heure (0-23) de la Happy Hour du jour, tiree deterministement (meme pour tout le monde). */
    public int getHeureHappyHour() {
        return new Random(seedDuJour()).nextInt(24);
    }

    public boolean isHappyHourActive() {
        return LocalTime.now().getHour() == getHeureHappyHour();
    }

    public double getHappyHourReductionPourcent() {
        return get("happy-hour-reduction-pourcent", 15);
    }

    // ---- Article du jour ("prix casse") ----

    /** Cle "categorie:item" de l'article du jour, ou null si la boutique est vide. */
    public String getArticleDuJour(List<String> toutesLesClesTriees) {
        if (toutesLesClesTriees.isEmpty()) {
            return null;
        }
        int index = (int) Math.floorMod(seedDuJour() * 31, toutesLesClesTriees.size());
        return toutesLesClesTriees.get(index);
    }

    public boolean estArticleDuJour(String categoryId, String itemId, List<String> toutesLesClesTriees) {
        String cle = categoryId.toLowerCase() + ":" + itemId.toLowerCase();
        return cle.equals(getArticleDuJour(toutesLesClesTriees));
    }

    public double getPrixCasseReductionPourcent() {
        return get("prix-casse-pourcent", 40);
    }

    // ---- Premier achat ----

    public boolean aDejaAchete(UUID uuid) {
        String select = "SELECT 1 FROM boutique_premier_achat WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture premier achat pour " + uuid + " : " + e.getMessage());
            return true;
        }
    }

    public void marquerAchatEffectue(UUID uuid) {
        String insert = "INSERT OR IGNORE INTO boutique_premier_achat (uuid) VALUES (?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur marquage premier achat pour " + uuid + " : " + e.getMessage());
        }
    }

    public double getReductionPremierAchatPourcent() {
        return get("reduction-premier-achat-pourcent", 20);
    }

    // ---- Anniversaire d'inscription ----

    public boolean estAnniversaireAujourdhui(Player player) {
        LocalDate datePremiereConnexion = Instant.ofEpochMilli(player.getFirstPlayed())
                .atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate aujourdhui = LocalDate.now();
        return datePremiereConnexion.getMonthValue() == aujourdhui.getMonthValue()
                && datePremiereConnexion.getDayOfMonth() == aujourdhui.getDayOfMonth();
    }

    public double getReductionAnniversairePourcent() {
        return get("reduction-anniversaire-pourcent", 25);
    }

    // ---- Rang VIP (base sur permission) ----

    /** Meilleure reduction VIP applicable a ce joueur (0 si aucune permission VIP boutique). */
    public double getReductionVipPourcent(Player player) {
        double meilleure = 0;
        ConfigurationSection root = promotionsConfig.get().getConfigurationSection("vip");
        if (root == null) {
            return 0;
        }
        for (String permission : root.getKeys(false)) {
            if (player.hasPermission(permission)) {
                meilleure = Math.max(meilleure, root.getDouble(permission, 0));
            }
        }
        return meilleure;
    }

    // ---- Codes promo (actives explicitement via /boutique code XXXX) ----

    public void activerCode(Player player, String code) {
        codeActifParJoueur.put(player.getUniqueId(), code.toUpperCase());
    }

    public PromoCode getCodeActif(UUID uuid) {
        String code = codeActifParJoueur.get(uuid);
        return code == null ? null : codes.get(code);
    }

    public void retirerCodeActif(UUID uuid) {
        codeActifParJoueur.remove(uuid);
    }

    public PromoCode getCode(String code) {
        return code == null ? null : codes.get(code.toUpperCase());
    }

    /** Requete synchrone : a appeler hors du thread principal. */
    public int getUtilisationsCode(UUID uuid, String code) {
        String select = "SELECT utilisations FROM boutique_codes_utilises WHERE uuid = ? AND code = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, code.toUpperCase());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("utilisations") : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture utilisations code pour " + uuid + " : " + e.getMessage());
            return 0;
        }
    }

    public void enregistrerUtilisationCode(UUID uuid, String code) {
        String upsert = "INSERT INTO boutique_codes_utilises (uuid, code, utilisations) VALUES (?, ?, 1) " +
                "ON CONFLICT(uuid, code) DO UPDATE SET utilisations = utilisations + 1;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, code.toUpperCase());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement utilisation code pour " + uuid + " : " + e.getMessage());
        }
    }

    public double getReductionMaxPourcent() {
        return get("reduction-max-pourcent", 80);
    }

    // ---- Integration BattlePass (voir "idee : integrations inter-modules") ----

    public double getXpBattlepassPar100Argent() {
        return get("xp-battlepass-par-100-argent", 0);
    }

    /** Somme des reductions AUTOMATIQUES (hors code promo, gere separement) applicables a cet
     * achat, plafonnee a reduction-max-pourcent. */
    public double getReductionAutomatiquePourcent(Player player, String categoryId, String itemId, List<String> toutesLesClesTriees) {
        double total = 0;
        if (isHappyHourActive()) {
            total += getHappyHourReductionPourcent();
        }
        if (estArticleDuJour(categoryId, itemId, toutesLesClesTriees)) {
            total += getPrixCasseReductionPourcent();
        }
        if (!aDejaAchete(player.getUniqueId())) {
            total += getReductionPremierAchatPourcent();
        }
        if (estAnniversaireAujourdhui(player)) {
            total += getReductionAnniversairePourcent();
        }
        total += getReductionVipPourcent(player);
        total += loyaltyManager.getReductionPourcent(player.getUniqueId());
        return Math.min(total, getReductionMaxPourcent());
    }
}
