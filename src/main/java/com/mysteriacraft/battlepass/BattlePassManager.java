package com.mysteriacraft.battlepass;

import com.mysteriacraft.core.SeasonalWindow;
import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Charge les paliers du BattlePass depuis battlepass.yml et gere la progression des joueurs
 * (xp, statut premium, recompenses reclamees) en SQLite. Fonctionne par SAISONS (voir
 * checkAndArchiveSeasonIfNeeded) : a l'echeance de "saison.fin", xp et premium sont archives puis
 * remis a zero pour tous, comme un vrai plugin BattlePass.
 */
public class BattlePassManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager battlepassConfig;

    private final List<BattlePassLevel> levels = new ArrayList<>();
    private long xpPerMinute = 0;
    private double premiumPrice = 0;
    private Reward prestigeReward;
    private long firstQuestBonusXp = 0;
    private long sprintDurationSeconds = 3600;
    private double sprintMultiplier = 3.0;

    private int seasonNumero = 1;
    private String seasonNom = "Saison 1";
    private LocalDate seasonFin = LocalDate.now().plusMonths(3);
    private boolean seasonRotationEnabled = true;

    /** Fenetres d'evenement double xp (ou autre multiplicateur) optionnelles, voir "evenements-xp"
     * dans battlepass.yml. Reutilise le meme format actif-du/actif-au que les quetes saisonnieres. */
    public record XpEvent(String actifDu, String actifAu, double multiplicateur) {
        public boolean isActiveNow() {
            return SeasonalWindow.isActiveNow(actifDu, actifAu);
        }
    }

    private final List<XpEvent> xpEvents = new ArrayList<>();
    private final java.util.Map<Integer, String> chapterNames = new java.util.HashMap<>();

    public BattlePassManager(Plugin plugin, Database database, ConfigManager battlepassConfig) {
        this.plugin = plugin;
        this.database = database;
        this.battlepassConfig = battlepassConfig;
        createTables();
        loadLevels();
    }

    private void createTables() {
        String[] statements = {
                "CREATE TABLE IF NOT EXISTS battlepass_joueurs (" +
                        "uuid TEXT PRIMARY KEY, xp INTEGER NOT NULL DEFAULT 0, premium INTEGER NOT NULL DEFAULT 0);",
                "CREATE TABLE IF NOT EXISTS battlepass_reclamations (" +
                        "uuid TEXT NOT NULL, niveau INTEGER NOT NULL, piste TEXT NOT NULL, " +
                        "PRIMARY KEY (uuid, niveau, piste));",
                // Prestige : recommencer au niveau 1 apres le niveau max (voir /battlepass prestige).
                "CREATE TABLE IF NOT EXISTS battlepass_prestige (" +
                        "uuid TEXT NOT NULL PRIMARY KEY, prestige INTEGER NOT NULL DEFAULT 0);",
                // Historique d'xp gagnee par jour (voir /battlepass stats).
                "CREATE TABLE IF NOT EXISTS battlepass_xp_historique (" +
                        "uuid TEXT NOT NULL, jour TEXT NOT NULL, xp_gagne INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY (uuid, jour));",
                // Defi "sprint" (xp temporairement multipliee), limite a une fois par jour.
                "CREATE TABLE IF NOT EXISTS battlepass_sprint (" +
                        "uuid TEXT NOT NULL, jour TEXT NOT NULL, PRIMARY KEY (uuid, jour));",
                // Roulement fige d'un palier "mystere" (voir BattlePassLevel#mysteryPool), une fois par joueur.
                "CREATE TABLE IF NOT EXISTS battlepass_mystere_roule (" +
                        "uuid TEXT NOT NULL, niveau INTEGER NOT NULL, index_roule INTEGER NOT NULL, " +
                        "PRIMARY KEY (uuid, niveau));",
                // Titres de chat debloques et titre actuellement affiche (voir RewardType.TITRE_CHAT).
                "CREATE TABLE IF NOT EXISTS battlepass_titres_debloques (" +
                        "uuid TEXT NOT NULL, titre TEXT NOT NULL, PRIMARY KEY (uuid, titre));",
                "CREATE TABLE IF NOT EXISTS battlepass_titre_actif (" +
                        "uuid TEXT NOT NULL PRIMARY KEY, titre TEXT NOT NULL);",
                // Premiere quete terminee du jour (bonus xp, voir QuestService).
                "CREATE TABLE IF NOT EXISTS battlepass_premiere_quete (" +
                        "uuid TEXT NOT NULL, jour TEXT NOT NULL, PRIMARY KEY (uuid, jour));",
                // Petite table cle/valeur generique (voir getMeta/setMeta), utilisee pour retenir
                // quelle date de fin de saison a deja ete traitee (voir checkAndArchiveSeasonIfNeeded).
                "CREATE TABLE IF NOT EXISTS battlepass_meta (cle TEXT PRIMARY KEY, valeur TEXT NOT NULL);",
                // Niveau/xp final de chaque joueur a la fin de chaque saison passee (voir /battlepass historique).
                "CREATE TABLE IF NOT EXISTS battlepass_historique_saisons (" +
                        "uuid TEXT NOT NULL, saison INTEGER NOT NULL, niveau INTEGER NOT NULL, " +
                        "xp INTEGER NOT NULL, PRIMARY KEY (uuid, saison));"
        };
        Connection connection = database.getConnection();
        for (String sql : statements) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur creation table battlepass : " + e.getMessage());
            }
        }
        // Migration douce : ajoute la colonne "nom" (pour /battlepass top) si absente. L'erreur
        // "duplicate column" sur une base deja migree est normale et volontairement ignoree.
        try (PreparedStatement statement = connection.prepareStatement("ALTER TABLE battlepass_joueurs ADD COLUMN nom TEXT;")) {
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // Colonne deja presente.
        }
    }

    public void loadLevels() {
        levels.clear();
        xpEvents.clear();
        chapterNames.clear();
        ConfigurationSection chapitresSection = battlepassConfig.get().getConfigurationSection("chapitres");
        if (chapitresSection != null) {
            for (String key : chapitresSection.getKeys(false)) {
                try {
                    chapterNames.put(Integer.parseInt(key), chapitresSection.getString(key, "Chapitre " + key));
                } catch (NumberFormatException ignored) {
                    // Cle de chapitre invalide : ignoree.
                }
            }
        }
        xpPerMinute = battlepassConfig.get().getLong("xp-par-minute-de-jeu", 0);
        premiumPrice = battlepassConfig.get().getDouble("prix-premium", 0);
        firstQuestBonusXp = battlepassConfig.get().getLong("bonus-premiere-quete-du-jour", 0);
        sprintDurationSeconds = battlepassConfig.get().getLong("sprint-duree-secondes", 3600);
        sprintMultiplier = battlepassConfig.get().getDouble("sprint-multiplicateur", 3.0);
        prestigeReward = RewardParser.parse(battlepassConfig.get().getConfigurationSection("prestige-recompense"));

        ConfigurationSection saisonSection = battlepassConfig.get().getConfigurationSection("saison");
        if (saisonSection != null) {
            seasonNumero = Math.max(1, saisonSection.getInt("numero", 1));
            seasonNom = saisonSection.getString("nom", "Saison " + seasonNumero);
            seasonRotationEnabled = saisonSection.getBoolean("rotation", true);
            try {
                seasonFin = LocalDate.parse(saisonSection.getString("fin", LocalDate.now().plusMonths(3).toString()));
            } catch (Exception e) {
                plugin.getLogger().warning("Date de fin de saison invalide dans battlepass.yml (format attendu AAAA-MM-JJ) : " + e.getMessage());
                seasonFin = LocalDate.now().plusMonths(3);
            }
        }

        List<?> eventsRaw = battlepassConfig.get().getMapList("evenements-xp");
        for (Object raw : eventsRaw) {
            if (raw instanceof java.util.Map<?, ?> map) {
                String du = String.valueOf(map.get("actif-du"));
                String au = String.valueOf(map.get("actif-au"));
                double multiplicateur = map.get("multiplicateur") instanceof Number n ? n.doubleValue() : 2.0;
                xpEvents.add(new XpEvent(du, au, multiplicateur));
            }
        }

        ConfigurationSection root = battlepassConfig.get().getConfigurationSection("niveaux");
        if (root == null) {
            plugin.getLogger().warning("Aucun palier trouve dans battlepass.yml (section 'niveaux' manquante).");
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                int level = Integer.parseInt(key);
                long xpRequired = section.getLong("xp-requis", 0);
                BattlePassReward free = parseReward(section.getConfigurationSection("gratuit"));
                BattlePassReward premium = parseReward(section.getConfigurationSection("premium"));
                int chapitre = Math.max(0, section.getInt("chapitre", 0));

                List<BattlePassReward> mysteryPool = new ArrayList<>();
                List<?> mysteryRaw = section.getMapList("mystere-pool");
                for (int i = 0; i < mysteryRaw.size(); i++) {
                    ConfigurationSection mysterySection = section.createSection("mystere-pool-temp-" + i, (java.util.Map<?, ?>) mysteryRaw.get(i));
                    BattlePassReward option = parseReward(mysterySection);
                    if (option != null) {
                        mysteryPool.add(option);
                    }
                    section.set("mystere-pool-temp-" + i, null);
                }
                if (!mysteryPool.isEmpty()) {
                    free = null;
                }

                levels.add(new BattlePassLevel(level, xpRequired, free, premium, mysteryPool, chapitre));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Cle de palier invalide dans battlepass.yml : " + key);
            }
        }
        levels.sort(Comparator.comparingInt(BattlePassLevel::level));
        plugin.getLogger().info(levels.size() + " palier(s) de BattlePass charge(s).");
    }

    private BattlePassReward parseReward(ConfigurationSection section) {
        Reward reward = RewardParser.parse(section);
        return reward == null ? null : new BattlePassReward(reward);
    }

    public List<BattlePassLevel> getLevels() {
        return levels;
    }

    public long getXpPerMinute() {
        return xpPerMinute;
    }

    public double getPremiumPrice() {
        return premiumPrice;
    }

    public long getFirstQuestBonusXp() {
        return firstQuestBonusXp;
    }

    public long getSprintDurationSeconds() {
        return sprintDurationSeconds;
    }

    public double getSprintMultiplier() {
        return sprintMultiplier;
    }

    public Reward getPrestigeReward() {
        return prestigeReward;
    }

    public int getSeasonNumero() {
        return seasonNumero;
    }

    public String getSeasonNom() {
        return seasonNom;
    }

    public LocalDate getSeasonFin() {
        return seasonFin;
    }

    public boolean isSeasonEnded() {
        return LocalDate.now().isAfter(seasonFin);
    }

    public long getSeasonDaysRemaining() {
        return Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), seasonFin));
    }

    public boolean isSeasonRotationEnabled() {
        return seasonRotationEnabled;
    }

    /** Multiplicateur d'xp cumule de tous les evenements actuellement actifs (voir "evenements-xp"),
     * 1.0 si aucun n'est actif. Plusieurs evenements simultanes se MULTIPLIENT entre eux. */
    public double getActiveEventMultiplier() {
        double multiplier = 1.0;
        for (XpEvent event : xpEvents) {
            if (event.isActiveNow()) {
                multiplier *= event.multiplicateur();
            }
        }
        return multiplier;
    }

    public String getChapterName(int chapitre) {
        return chapterNames.getOrDefault(chapitre, "Chapitre " + chapitre);
    }

    public int getMaxLevelNumber() {
        int max = 0;
        for (BattlePassLevel level : levels) {
            max = Math.max(max, level.level());
        }
        return max;
    }

    /** Le palier atteint pour une quantite d'xp donnee (le plus haut niveau dont xp-requis <= xp). */
    public int computeLevel(long xp) {
        int current = 0;
        for (BattlePassLevel level : levels) {
            if (xp >= level.xpRequired()) {
                current = level.level();
            }
        }
        return current;
    }

    public BattlePassLevel getLevel(int level) {
        for (BattlePassLevel l : levels) {
            if (l.level() == level) {
                return l;
            }
        }
        return null;
    }

    // ---- Editeur de paliers en jeu (voir /battlepassadmin editeur) ----

    /** Cree un nouveau palier (xp-requis = dernier palier + 500, meme chapitre que le dernier,
     * sans recompense) et le sauvegarde immediatement dans battlepass.yml. Renvoie son numero. */
    public synchronized int addLevel() {
        int newLevel = getMaxLevelNumber() + 1;
        BattlePassLevel last = getLevel(getMaxLevelNumber());
        long xpRequired = last != null ? last.xpRequired() + 500 : 0;
        int chapitre = last != null ? last.chapitre() : 1;
        ConfigurationSection section = battlepassConfig.get().getConfigurationSection("niveaux");
        if (section == null) {
            section = battlepassConfig.get().createSection("niveaux");
        }
        ConfigurationSection nouveau = section.createSection(String.valueOf(newLevel));
        nouveau.set("xp-requis", xpRequired);
        nouveau.set("chapitre", chapitre);
        battlepassConfig.save();
        loadLevels();
        return newLevel;
    }

    /** Supprime un palier de battlepass.yml (les reclamations deja faites par les joueurs sur ce
     * palier restent en base, sans effet, mais ne genent rien). */
    public synchronized void removeLevel(int level) {
        ConfigurationSection section = battlepassConfig.get().getConfigurationSection("niveaux");
        if (section != null) {
            section.set(String.valueOf(level), null);
            battlepassConfig.save();
            loadLevels();
        }
    }

    public synchronized void setLevelXpRequired(int level, long xpRequired) {
        editLevelSection(level, section -> section.set("xp-requis", Math.max(0, xpRequired)));
    }

    public synchronized void setLevelChapitre(int level, int chapitre) {
        editLevelSection(level, section -> section.set("chapitre", Math.max(0, chapitre)));
    }

    /** Definit (ou efface si item == null) la recompense ITEM d'une piste ("gratuit"/"premium")
     * d'un palier, a partir d'un ItemStack (typiquement l'objet tenu en main par l'admin). */
    public synchronized void setLevelReward(int level, String piste, org.bukkit.inventory.ItemStack item) {
        editLevelSection(level, section -> {
            if (item == null || item.getType().isAir()) {
                section.set(piste, null);
                return;
            }
            section.set(piste + ".type", "ITEM");
            section.set(piste + ".materiel", item.getType().name());
            section.set(piste + ".quantite", item.getAmount());
        });
    }

    /** Definit la recompense d'une piste ("gratuit"/"premium") d'un palier comme une commande
     * console (voir RewardType.COMMANDE), executee avec {joueur} remplace par le nom du joueur. */
    public synchronized void setLevelRewardCommand(int level, String piste, String commande) {
        editLevelSection(level, section -> {
            section.set(piste + ".type", "COMMANDE");
            section.set(piste + ".commande", commande);
            section.set(piste + ".nom", commande);
        });
    }

    private void editLevelSection(int level, java.util.function.Consumer<ConfigurationSection> editor) {
        ConfigurationSection root = battlepassConfig.get().getConfigurationSection("niveaux");
        if (root == null) {
            return;
        }
        ConfigurationSection section = root.getConfigurationSection(String.valueOf(level));
        if (section == null) {
            return;
        }
        editor.accept(section);
        battlepassConfig.save();
        loadLevels();
    }

    // ---- Progression joueur ----

    public synchronized long getXp(UUID uuid) {
        String select = "SELECT xp FROM battlepass_joueurs WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("xp");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture xp battlepass pour " + uuid + " : " + e.getMessage());
        }
        return 0L;
    }

    public synchronized boolean isPremium(UUID uuid) {
        String select = "SELECT premium FROM battlepass_joueurs WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("premium") != 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture statut premium pour " + uuid + " : " + e.getMessage());
        }
        return false;
    }

    /** Ajoute de l'xp au joueur (creant son enregistrement si besoin) et renvoie la nouvelle xp
     * totale. Le nom est retenu pour /battlepass top (classement de la saison en cours). */
    public synchronized long addXp(UUID uuid, long amount, String nom) {
        long newXp = Math.max(0, getXp(uuid) + amount);
        String upsert = "INSERT INTO battlepass_joueurs (uuid, xp, premium, nom) VALUES (?, ?, 0, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET xp = excluded.xp, nom = excluded.nom;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, newXp);
            statement.setString(3, nom);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour xp battlepass pour " + uuid + " : " + e.getMessage());
        }
        return newXp;
    }

    public synchronized void setPremium(UUID uuid, boolean premium) {
        String upsert = "INSERT INTO battlepass_joueurs (uuid, xp, premium) VALUES (?, 0, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET premium = excluded.premium;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, premium ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour statut premium pour " + uuid + " : " + e.getMessage());
        }
    }

    public synchronized boolean hasClaimed(UUID uuid, int level, String track) {
        String select = "SELECT 1 FROM battlepass_reclamations WHERE uuid = ? AND niveau = ? AND piste = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, level);
            statement.setString(3, track);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur verification reclamation battlepass : " + e.getMessage());
            return false;
        }
    }

    public synchronized void markClaimed(UUID uuid, int level, String track) {
        String insert = "INSERT OR IGNORE INTO battlepass_reclamations (uuid, niveau, piste) VALUES (?, ?, ?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, level);
            statement.setString(3, track);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement reclamation battlepass : " + e.getMessage());
        }
    }

    /** Charge en un seul passage l'ensemble des reclamations d'un joueur (uuid#niveau#piste). A appeler hors thread principal. */
    public synchronized Set<String> getAllClaims(UUID uuid) {
        Set<String> claims = new HashSet<>();
        String select = "SELECT niveau, piste FROM battlepass_reclamations WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    claims.add(rs.getInt("niveau") + "#" + rs.getString("piste"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture reclamations battlepass pour " + uuid + " : " + e.getMessage());
        }
        return claims;
    }

    // ---- Prestige ----

    public synchronized int getPrestige(UUID uuid) {
        String select = "SELECT prestige FROM battlepass_prestige WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("prestige");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture prestige pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    /** Reinitialise l'xp a 0 et incremente le prestige. A appeler seulement si le joueur est au
     * niveau max (verifie par l'appelant, voir BattlePassService#prestige). Renvoie le nouveau prestige. */
    public synchronized int prestige(UUID uuid) {
        int newPrestige = getPrestige(uuid) + 1;
        Connection connection = database.getConnection();
        String resetXp = "UPDATE battlepass_joueurs SET xp = 0 WHERE uuid = ?;";
        try (PreparedStatement statement = connection.prepareStatement(resetXp)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur reinitialisation xp pour prestige de " + uuid + " : " + e.getMessage());
        }
        String upsert = "INSERT INTO battlepass_prestige (uuid, prestige) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET prestige = excluded.prestige;";
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, newPrestige);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour prestige pour " + uuid + " : " + e.getMessage());
        }
        return newPrestige;
    }

    // ---- Historique d'xp (voir /battlepass stats) ----

    public synchronized void recordXpGain(UUID uuid, long amount) {
        if (amount == 0) {
            return;
        }
        String jour = LocalDate.now().toString();
        String upsert = "INSERT INTO battlepass_xp_historique (uuid, jour, xp_gagne) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, jour) DO UPDATE SET xp_gagne = xp_gagne + excluded.xp_gagne;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, jour);
            statement.setLong(3, amount);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement historique xp pour " + uuid + " : " + e.getMessage());
        }
    }

    public record XpHistoryEntry(String jour, long xpGagne) {
    }

    public synchronized List<XpHistoryEntry> getRecentXpHistory(UUID uuid, int days) {
        List<XpHistoryEntry> entries = new ArrayList<>();
        String select = "SELECT jour, xp_gagne FROM battlepass_xp_historique WHERE uuid = ? ORDER BY jour DESC LIMIT ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, days);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new XpHistoryEntry(rs.getString("jour"), rs.getLong("xp_gagne")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture historique xp pour " + uuid + " : " + e.getMessage());
        }
        return entries;
    }

    // ---- Sprint (defi manuel, xp multipliee, une fois par jour) ----

    public synchronized boolean hasUsedSprintToday(UUID uuid) {
        String select = "SELECT 1 FROM battlepass_sprint WHERE uuid = ? AND jour = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, LocalDate.now().toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture sprint pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    public synchronized void markSprintUsedToday(UUID uuid) {
        String insert = "INSERT INTO battlepass_sprint (uuid, jour) VALUES (?, ?) ON CONFLICT DO NOTHING;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, LocalDate.now().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement sprint pour " + uuid + " : " + e.getMessage());
        }
    }

    // ---- Palier "mystere" (voir BattlePassLevel#mysteryPool) ----

    /** Renvoie la recompense EFFECTIVE (deja roulee et figee pour ce joueur) d'un palier mystere,
     * en la tirant au hasard et en la persistant a la premiere consultation. */
    public synchronized BattlePassReward resolveMysteryReward(UUID uuid, BattlePassLevel level) {
        if (level.mysteryPool().isEmpty()) {
            return null;
        }
        String select = "SELECT index_roule FROM battlepass_mystere_roule WHERE uuid = ? AND niveau = ?;";
        Connection connection = database.getConnection();
        Integer rolled = null;
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, level.level());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    rolled = rs.getInt("index_roule");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture roulement mystere pour " + uuid + " : " + e.getMessage());
        }

        if (rolled == null) {
            rolled = new Random().nextInt(level.mysteryPool().size());
            String insert = "INSERT INTO battlepass_mystere_roule (uuid, niveau, index_roule) VALUES (?, ?, ?);";
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, level.level());
                statement.setInt(3, rolled);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur enregistrement roulement mystere pour " + uuid + " : " + e.getMessage());
            }
        }
        return level.mysteryPool().get(Math.min(rolled, level.mysteryPool().size() - 1));
    }

    // ---- Titres de chat (voir RewardType.TITRE_CHAT) ----

    public synchronized void unlockTitle(UUID uuid, String titre) {
        String insert = "INSERT INTO battlepass_titres_debloques (uuid, titre) VALUES (?, ?) ON CONFLICT DO NOTHING;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, titre);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deblocage titre pour " + uuid + " : " + e.getMessage());
        }
    }

    public synchronized Set<String> getUnlockedTitles(UUID uuid) {
        Set<String> titres = new HashSet<>();
        String select = "SELECT titre FROM battlepass_titres_debloques WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    titres.add(rs.getString("titre"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture titres debloques pour " + uuid + " : " + e.getMessage());
        }
        return titres;
    }

    public synchronized void setActiveTitle(UUID uuid, String titre) {
        Connection connection = database.getConnection();
        if (titre == null) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM battlepass_titre_actif WHERE uuid = ?;")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur suppression titre actif pour " + uuid + " : " + e.getMessage());
            }
            return;
        }
        String upsert = "INSERT INTO battlepass_titre_actif (uuid, titre) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET titre = excluded.titre;";
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, titre);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour titre actif pour " + uuid + " : " + e.getMessage());
        }
    }

    public synchronized String getActiveTitle(UUID uuid) {
        String select = "SELECT titre FROM battlepass_titre_actif WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("titre");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture titre actif pour " + uuid + " : " + e.getMessage());
        }
        return null;
    }

    // ---- Bonus xp pour la premiere quete terminee du jour (voir QuestService) ----

    /** Enregistre qu'une quete a ete terminee aujourd'hui pour ce joueur, et renvoie true si
     * c'etait la PREMIERE de la journee (donc si le bonus doit etre donne). */
    public synchronized boolean registerQuestCompletionAndCheckFirstOfDay(UUID uuid) {
        String jour = LocalDate.now().toString();
        String insert = "INSERT INTO battlepass_premiere_quete (uuid, jour) VALUES (?, ?) ON CONFLICT DO NOTHING;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, jour);
            int inserted = statement.executeUpdate();
            return inserted > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur verification premiere quete du jour pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    // ---- Meta cle/valeur generique (voir checkAndArchiveSeasonIfNeeded) ----

    private synchronized String getMeta(String cle) {
        String select = "SELECT valeur FROM battlepass_meta WHERE cle = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, cle);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("valeur");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture meta battlepass " + cle + " : " + e.getMessage());
        }
        return null;
    }

    private synchronized void setMeta(String cle, String valeur) {
        String upsert = "INSERT INTO battlepass_meta (cle, valeur) VALUES (?, ?) " +
                "ON CONFLICT(cle) DO UPDATE SET valeur = excluded.valeur;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, cle);
            statement.setString(2, valeur);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur ecriture meta battlepass " + cle + " : " + e.getMessage());
        }
    }

    // ---- Saisons (voir la doc en tete de battlepass.yml) ----

    private static final String META_SAISON_FIN_TRAITEE = "saison-fin-traitee";

    /** A appeler au demarrage et a chaque /battlepassadmin reload : archive et remet a zero la
     * saison en cours si sa date de fin (saison.fin) est depassee ET n'a pas deja ete traitee
     * (evite un double archivage si la date reste inchangee apres un reload). Renvoie true si un
     * archivage a eu lieu. */
    public synchronized boolean checkAndArchiveSeasonIfNeeded() {
        if (!isSeasonEnded()) {
            return false;
        }
        String finTraitee = getMeta(META_SAISON_FIN_TRAITEE);
        if (seasonFin.toString().equals(finTraitee)) {
            return false;
        }
        archiverSaison(seasonNumero);
        setMeta(META_SAISON_FIN_TRAITEE, seasonFin.toString());
        return true;
    }

    /** /battlepassadmin nouvellesaison : force l'archivage/reset immediatement, sans attendre la
     * date de fin configuree (utile pour terminer une saison en avance). */
    public synchronized void forceArchiveSeasonNow() {
        archiverSaison(seasonNumero);
        setMeta(META_SAISON_FIN_TRAITEE, seasonFin.toString());
    }

    /** Archive le niveau/xp final de chaque joueur ayant progresse cette saison, puis remet a zero
     * xp et statut premium POUR TOUS (le premium doit donc etre rachete a chaque nouvelle saison,
     * comme dans un vrai battle pass). Prestige et titres debloques NE SONT PAS touches : ce sont
     * des progressions permanentes, separees des saisons. */
    private void archiverSaison(int numeroTermine) {
        Connection connection = database.getConnection();
        String insertHistorique = "INSERT INTO battlepass_historique_saisons (uuid, saison, niveau, xp) " +
                "SELECT uuid, ?, 0, xp FROM battlepass_joueurs WHERE xp > 0 " +
                "ON CONFLICT(uuid, saison) DO UPDATE SET xp = excluded.xp;";
        try (PreparedStatement statement = connection.prepareStatement(insertHistorique)) {
            statement.setInt(1, numeroTermine);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur archivage historique de la saison " + numeroTermine + " : " + e.getMessage());
        }

        try (PreparedStatement select = connection.prepareStatement("SELECT uuid, xp FROM battlepass_historique_saisons WHERE saison = ?;")) {
            select.setInt(1, numeroTermine);
            try (ResultSet rs = select.executeQuery();
                 PreparedStatement updateNiveau = connection.prepareStatement(
                         "UPDATE battlepass_historique_saisons SET niveau = ? WHERE uuid = ? AND saison = ?;")) {
                while (rs.next()) {
                    long xp = rs.getLong("xp");
                    updateNiveau.setInt(1, computeLevel(xp));
                    updateNiveau.setString(2, rs.getString("uuid"));
                    updateNiveau.setInt(3, numeroTermine);
                    updateNiveau.addBatch();
                }
                updateNiveau.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur calcul des niveaux finaux de la saison " + numeroTermine + " : " + e.getMessage());
        }

        try (PreparedStatement reset = connection.prepareStatement("UPDATE battlepass_joueurs SET xp = 0, premium = 0;")) {
            reset.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur reinitialisation xp/premium pour la nouvelle saison : " + e.getMessage());
        }
        try (PreparedStatement clearClaims = connection.prepareStatement("DELETE FROM battlepass_reclamations;")) {
            clearClaims.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur reinitialisation des reclamations pour la nouvelle saison : " + e.getMessage());
        }
        try (PreparedStatement clearMystere = connection.prepareStatement("DELETE FROM battlepass_mystere_roule;")) {
            clearMystere.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur reinitialisation des roulements mystere pour la nouvelle saison : " + e.getMessage());
        }

        if (!seasonRotationEnabled) {
            archiveLevelsConfigToFile(numeroTermine);
            battlepassConfig.get().set("niveaux", null);
            battlepassConfig.save();
            loadLevels();
        }
        plugin.getLogger().info("BattlePass : saison " + numeroTermine + " archivee, xp/premium reinitialises pour tous.");
    }

    /** Sauvegarde la section "niveaux" actuelle dans un fichier separe avant de la vider (voir
     * "rotation: false" dans battlepass.yml), pour ne jamais perdre la configuration des paliers
     * d'une saison passee. */
    private void archiveLevelsConfigToFile(int numeroTermine) {
        ConfigurationSection niveauxSection = battlepassConfig.get().getConfigurationSection("niveaux");
        if (niveauxSection == null) {
            return;
        }
        try {
            java.io.File dossier = new java.io.File(plugin.getDataFolder(), "battlepass_archives");
            if (!dossier.exists()) {
                dossier.mkdirs();
            }
            java.io.File fichier = new java.io.File(dossier, "saison-" + numeroTermine + ".yml");
            org.bukkit.configuration.file.YamlConfiguration archive = new org.bukkit.configuration.file.YamlConfiguration();
            archive.set("niveaux", niveauxSection);
            archive.save(fichier);
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur archivage des paliers de la saison " + numeroTermine + " : " + e.getMessage());
        }
    }

    /** Classement de la saison EN COURS (voir /battlepass top), trie par xp decroissante. */
    public record TopEntry(String nom, long xp, int niveau) {
    }

    public synchronized List<TopEntry> getTopPlayers(int limit) {
        List<TopEntry> entries = new ArrayList<>();
        String select = "SELECT nom, xp FROM battlepass_joueurs WHERE xp > 0 ORDER BY xp DESC LIMIT ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long xp = rs.getLong("xp");
                    String nom = rs.getString("nom");
                    entries.add(new TopEntry(nom != null ? nom : "?", xp, computeLevel(xp)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture classement battlepass : " + e.getMessage());
        }
        return entries;
    }

    /** Historique des saisons passees d'un joueur (voir /battlepass historique), triee de la plus recente a la plus ancienne. */
    public record SeasonHistoryEntry(int saison, int niveau, long xp) {
    }

    public synchronized List<SeasonHistoryEntry> getSeasonHistory(UUID uuid) {
        List<SeasonHistoryEntry> entries = new ArrayList<>();
        String select = "SELECT saison, niveau, xp FROM battlepass_historique_saisons WHERE uuid = ? ORDER BY saison DESC;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new SeasonHistoryEntry(rs.getInt("saison"), rs.getInt("niveau"), rs.getLong("xp")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture historique des saisons pour " + uuid + " : " + e.getMessage());
        }
        return entries;
    }
}
