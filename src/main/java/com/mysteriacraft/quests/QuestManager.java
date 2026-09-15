package com.mysteriacraft.quests;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Charge les quetes depuis quests.yml et gere leur progression en SQLite.
 * Le reset (quotidien/hebdomadaire) est implicite : la progression est stockee sous une cle
 * de periode calculee a partir de la date courante ; des que cette cle change, une nouvelle
 * ligne vierge est utilisee automatiquement, sans tache planifiee.
 */
public class QuestManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager questsConfig;

    private final List<QuestDefinition> dailyQuests = new ArrayList<>();
    private final List<QuestDefinition> weeklyQuests = new ArrayList<>();
    private final List<QuestDefinition> permanentQuests = new ArrayList<>();

    /** Petit contrat minimal pour lire le niveau de BattlePass d'un joueur, implemente par
     * BattlePassManager (branche apres coup depuis MysteriaCraft, voir "niveau-pass-requis"). */
    public interface BattlePassLevelProvider {
        int getLevel(UUID uuid);
    }

    private BattlePassLevelProvider battlePassLevelProvider;

    public void setBattlePassLevelProvider(BattlePassLevelProvider battlePassLevelProvider) {
        this.battlePassLevelProvider = battlePassLevelProvider;
    }

    /** Vrai si le joueur remplit la condition "niveau-pass-requis" de cette quete (toujours vrai
     * si non definie, ou si le fournisseur n'est pas encore branche). */
    public boolean meetsPassRequirement(UUID uuid, QuestDefinition quest) {
        if (quest.niveauPassRequis() <= 0 || battlePassLevelProvider == null) {
            return true;
        }
        return battlePassLevelProvider.getLevel(uuid) >= quest.niveauPassRequis();
    }

    public QuestManager(Plugin plugin, Database database, ConfigManager questsConfig) {
        this.plugin = plugin;
        this.database = database;
        this.questsConfig = questsConfig;
        createTable();
        loadQuests();
    }

    private void createTable() {
        String[] statements = {
                "CREATE TABLE IF NOT EXISTS quest_progression (" +
                        "uuid TEXT NOT NULL, quest_id TEXT NOT NULL, periode_cle TEXT NOT NULL, " +
                        "progression INTEGER NOT NULL DEFAULT 0, reclame INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY (uuid, quest_id, periode_cle));",
                // Suivi de streak (jours consecutifs avec au moins une quete quotidienne terminee).
                "CREATE TABLE IF NOT EXISTS quest_streak (" +
                        "uuid TEXT NOT NULL PRIMARY KEY, dernier_jour TEXT NOT NULL, streak INTEGER NOT NULL DEFAULT 0);",
                // Types de quete DIFFERENTS termines cette semaine (quete "polyvalence").
                "CREATE TABLE IF NOT EXISTS quest_types_hebdo (" +
                        "uuid TEXT NOT NULL, semaine_cle TEXT NOT NULL, type TEXT NOT NULL, " +
                        "PRIMARY KEY (uuid, semaine_cle, type));",
                // Echange (reroll) d'une quete quotidienne imposee contre une autre du meme pool.
                "CREATE TABLE IF NOT EXISTS quest_echanges (" +
                        "uuid TEXT NOT NULL, periode_cle TEXT NOT NULL, quest_originale_id TEXT NOT NULL, " +
                        "quest_remplacement_id TEXT NOT NULL, PRIMARY KEY (uuid, periode_cle, quest_originale_id));",
                // Boost (xp/recompense doublee) achete sur une quete precise pour la periode courante.
                "CREATE TABLE IF NOT EXISTS quest_boosts (" +
                        "uuid TEXT NOT NULL, quest_id TEXT NOT NULL, periode_cle TEXT NOT NULL, " +
                        "PRIMARY KEY (uuid, quest_id, periode_cle));",
                // Historique des quetes terminees (consultable via /quests historique).
                "CREATE TABLE IF NOT EXISTS quest_historique (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, quest_nom TEXT NOT NULL, " +
                        "xp INTEGER NOT NULL, date_iso TEXT NOT NULL);",
                // Contrat (quete optionnelle plus difficile) explicitement accepte par le joueur.
                "CREATE TABLE IF NOT EXISTS quest_contrats (" +
                        "uuid TEXT NOT NULL, quest_id TEXT NOT NULL, periode_cle TEXT NOT NULL, " +
                        "PRIMARY KEY (uuid, quest_id, periode_cle));",
                // Biomes DIFFERENTS visites cette semaine (quete d'exploration).
                "CREATE TABLE IF NOT EXISTS quest_biomes_vus (" +
                        "uuid TEXT NOT NULL, semaine_cle TEXT NOT NULL, biome TEXT NOT NULL, " +
                        "PRIMARY KEY (uuid, semaine_cle, biome));"
        };
        Connection connection = database.getConnection();
        for (String sql : statements) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur creation table quetes : " + e.getMessage());
            }
        }
    }

    public void loadQuests() {
        dailyQuests.clear();
        weeklyQuests.clear();
        permanentQuests.clear();
        loadSection("quotidiennes", QuestPeriod.DAILY, dailyQuests);
        loadSection("hebdomadaires", QuestPeriod.WEEKLY, weeklyQuests);
        loadSection("hautsfaits", QuestPeriod.PERMANENT, permanentQuests);
        plugin.getLogger().info((dailyQuests.size() + weeklyQuests.size() + permanentQuests.size()) + " quete(s)/haut(s) fait(s) charge(s) depuis quests.yml.");
    }

    private void loadSection(String sectionName, QuestPeriod period, List<QuestDefinition> target) {
        ConfigurationSection root = questsConfig.get().getConfigurationSection(sectionName);
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                target.add(parseQuest(id, section, period));
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement quete '" + id + "' : " + e.getMessage());
            }
        }
    }

    private QuestDefinition parseQuest(String id, ConfigurationSection section, QuestPeriod period) {
        String displayName = section.getString("nom", id);
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "PAPER"));
        if (iconMaterial == null) {
            iconMaterial = Material.PAPER;
        }
        String lore = section.contains("lore") ? section.getString("lore") : null;
        ItemBuilder iconBuilder = new ItemBuilder(iconMaterial).name(displayName);
        if (lore != null) {
            iconBuilder.lore(List.of(lore));
        }
        ItemStack icon = iconBuilder.build();

        QuestType type = QuestType.fromString(section.getString("type", "BREAK_BLOCK"));
        String target = section.getString("cible", null);
        int objective = Math.max(1, section.getInt("objectif", 1));
        long xp = section.getLong("xp", 0);
        Reward reward = RewardParser.parse(section.getConfigurationSection("recompense"));

        // Quete saisonniere optionnelle (Halloween, Noel...) : actif-du/actif-au (format "MM-jj").
        // Absent des deux cotes = quete permanente (comportement par defaut, inchange).
        String actifDu = section.contains("actif-du") ? section.getString("actif-du") : null;
        String actifAu = section.contains("actif-au") ? section.getString("actif-au") : null;

        QuestDefinition.WeatherCondition meteo = QuestDefinition.WeatherCondition.fromString(
                section.contains("meteo") ? section.getString("meteo") : null);
        String reveleeApres = section.contains("revelee-apres") ? section.getString("revelee-apres") : null;
        boolean contrat = section.getBoolean("contrat", false);
        int niveauPassRequis = Math.max(0, section.getInt("niveau-pass-requis", 0));

        return new QuestDefinition(id, displayName, icon, type, target, objective, xp, reward, period, actifDu, actifAu,
                meteo, reveleeApres, contrat, lore, niveauPassRequis);
    }

    /** Quetes quotidiennes ACTUELLEMENT actives (exclut une quete saisonniere hors periode). */
    public List<QuestDefinition> getDailyQuests() {
        return activeOnly(dailyQuests);
    }

    /** Quetes hebdomadaires ACTUELLEMENT actives (exclut une quete saisonniere hors periode). */
    public List<QuestDefinition> getWeeklyQuests() {
        return activeOnly(weeklyQuests);
    }

    /** Hauts faits permanents ACTUELLEMENT actifs (ne se reinitialisent jamais, voir QuestPeriod.PERMANENT). */
    public List<QuestDefinition> getPermanentQuests() {
        return activeOnly(permanentQuests);
    }

    public List<QuestDefinition> getAllQuests() {
        List<QuestDefinition> all = new ArrayList<>(getDailyQuests());
        all.addAll(getWeeklyQuests());
        all.addAll(getPermanentQuests());
        return all;
    }

    /** Retrouve une quete par id, TOUS pools confondus (y compris hors periode active), utile
     * pour resoudre un id de remplacement stocke dans quest_echanges. */
    private QuestDefinition findQuestById(String id) {
        for (List<QuestDefinition> pool : List.of(dailyQuests, weeklyQuests, permanentQuests)) {
            for (QuestDefinition quest : pool) {
                if (quest.id().equals(id)) {
                    return quest;
                }
            }
        }
        return null;
    }

    private List<QuestDefinition> activeOnly(List<QuestDefinition> quests) {
        List<QuestDefinition> active = new ArrayList<>();
        for (QuestDefinition quest : quests) {
            if (quest.isActiveNow()) {
                active.add(quest);
            }
        }
        return active;
    }

    /** Cle de periode courante (change automatiquement a minuit / au changement de semaine ISO). */
    public String currentPeriodKey(QuestPeriod period) {
        if (period == QuestPeriod.PERMANENT) {
            return "PERMANENT";
        }
        LocalDate today = LocalDate.now();
        if (period == QuestPeriod.DAILY) {
            return "J-" + today;
        }
        WeekFields weekFields = WeekFields.of(Locale.FRANCE);
        int week = today.get(weekFields.weekOfWeekBasedYear());
        int weekYear = today.get(weekFields.weekBasedYear());
        return "S-" + weekYear + "-" + week;
    }

    public record ProgressSnapshot(int progression, boolean complete) {
    }

    public synchronized ProgressSnapshot getProgress(UUID uuid, QuestDefinition quest) {
        String periodKey = currentPeriodKey(quest.period());
        String select = "SELECT progression, reclame FROM quest_progression WHERE uuid = ? AND quest_id = ? AND periode_cle = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, quest.id());
            statement.setString(3, periodKey);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new ProgressSnapshot(rs.getInt("progression"), rs.getInt("reclame") != 0);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture progression quete '" + quest.id() + "' pour " + uuid + " : " + e.getMessage());
        }
        return new ProgressSnapshot(0, false);
    }

    public record ProgressResult(int newProgression, boolean justCompleted) {
    }

    /**
     * Ajoute de la progression a une quete pour la periode courante. Si la quete est deja
     * marquee "reclame" (donc terminee et deja recompensee cette periode), ne fait rien.
     * La quete est automatiquement marquee "reclame" des que l'objectif est atteint
     * (recompense donnee automatiquement par QuestService, pas de reclamation manuelle).
     */
    public synchronized ProgressResult incrementProgress(UUID uuid, QuestDefinition quest, int amount) {
        String periodKey = currentPeriodKey(quest.period());
        ProgressSnapshot current = getProgress(uuid, quest);
        if (current.complete()) {
            return new ProgressResult(current.progression(), false);
        }

        int newProgression = Math.min(quest.objective(), current.progression() + amount);
        boolean justCompleted = newProgression >= quest.objective();

        String upsert = "INSERT INTO quest_progression (uuid, quest_id, periode_cle, progression, reclame) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid, quest_id, periode_cle) DO UPDATE SET progression = excluded.progression, reclame = excluded.reclame;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, quest.id());
            statement.setString(3, periodKey);
            statement.setInt(4, newProgression);
            statement.setInt(5, justCompleted ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour progression quete '" + quest.id() + "' pour " + uuid + " : " + e.getMessage());
        }

        return new ProgressResult(newProgression, justCompleted);
    }

    /**
     * Fixe la progression a la valeur donnee SI elle est superieure a l'actuelle (jamais
     * decroissante), contrairement a incrementProgress qui ADDITIONNE. Utilise pour les quetes
     * dont la progression est un ETAT courant plutot qu'un cumul (ex: COLLECT_DISTINCT_CUSTOM_ITEMS,
     * nombre d'objets custom differents possedes EN CE MOMENT).
     */
    public synchronized ProgressResult setProgressIfHigher(UUID uuid, QuestDefinition quest, int value) {
        ProgressSnapshot current = getProgress(uuid, quest);
        if (current.complete() || value <= current.progression()) {
            return new ProgressResult(current.progression(), false);
        }
        return incrementProgress(uuid, quest, value - current.progression());
    }

    // ---- Streak (quete "polyvalence"/"streak", voir QuestService) ----

    /** Enregistre qu'au moins une quete quotidienne a ete terminee AUJOURD'HUI pour ce joueur, et
     * renvoie le nouveau streak (jours consecutifs). Ne compte qu'une fois par jour. */
    public synchronized int registerDailyStreak(UUID uuid) {
        LocalDate today = LocalDate.now();
        String select = "SELECT dernier_jour, streak FROM quest_streak WHERE uuid = ?;";
        Connection connection = database.getConnection();
        LocalDate lastDay = null;
        int streak = 0;
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    lastDay = LocalDate.parse(rs.getString("dernier_jour"));
                    streak = rs.getInt("streak");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture streak quetes pour " + uuid + " : " + e.getMessage());
        }

        if (today.equals(lastDay)) {
            return streak;
        }
        streak = today.equals(lastDay == null ? null : lastDay.plusDays(1)) ? streak + 1 : 1;

        String upsert = "INSERT INTO quest_streak (uuid, dernier_jour, streak) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET dernier_jour = excluded.dernier_jour, streak = excluded.streak;";
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, today.toString());
            statement.setInt(3, streak);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour streak quetes pour " + uuid + " : " + e.getMessage());
        }
        return streak;
    }

    // ---- Polyvalence hebdomadaire (types de quete differents termines cette semaine) ----

    public synchronized int registerWeeklyTypeCompletion(UUID uuid, QuestType type) {
        String semaineCle = currentPeriodKey(QuestPeriod.WEEKLY);
        String insert = "INSERT INTO quest_types_hebdo (uuid, semaine_cle, type) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, semaineCle);
            statement.setString(3, type.name());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement type hebdo pour " + uuid + " : " + e.getMessage());
        }

        String count = "SELECT COUNT(*) AS n FROM quest_types_hebdo WHERE uuid = ? AND semaine_cle = ?;";
        try (PreparedStatement statement = connection.prepareStatement(count)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, semaineCle);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("n");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur comptage types hebdo pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    // ---- Echange (reroll) d'une quete quotidienne ----

    public synchronized int countExchangesToday(UUID uuid) {
        String periodeCle = currentPeriodKey(QuestPeriod.DAILY);
        String sql = "SELECT COUNT(*) AS n FROM quest_echanges WHERE uuid = ? AND periode_cle = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, periodeCle);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("n");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur comptage echanges pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    /** Echange la quete quotidienne "originalId" (0 progression requise) contre une autre du
     * pool quotidien non deja active pour ce joueur. Renvoie la quete de remplacement, ou null
     * si aucun echange possible (id invalide, deja echangee, ou aucune autre quete disponible). */
    public synchronized QuestDefinition exchangeDailyQuest(UUID uuid, String originalId) {
        QuestDefinition original = findQuestById(originalId);
        if (original == null || original.period() != QuestPeriod.DAILY) {
            return null;
        }
        Set<String> unavailable = new HashSet<>();
        unavailable.add(originalId);
        String periodeCle = currentPeriodKey(QuestPeriod.DAILY);
        String sql = "SELECT quest_originale_id, quest_remplacement_id FROM quest_echanges WHERE uuid = ? AND periode_cle = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, periodeCle);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    unavailable.add(rs.getString("quest_originale_id"));
                    unavailable.add(rs.getString("quest_remplacement_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture echanges pour " + uuid + " : " + e.getMessage());
        }

        List<QuestDefinition> candidates = new ArrayList<>();
        for (QuestDefinition quest : getDailyQuests()) {
            if (!unavailable.contains(quest.id())) {
                candidates.add(quest);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        QuestDefinition replacement = candidates.get(new Random().nextInt(candidates.size()));

        String insert = "INSERT INTO quest_echanges (uuid, periode_cle, quest_originale_id, quest_remplacement_id) VALUES (?, ?, ?, ?);";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, periodeCle);
            statement.setString(3, originalId);
            statement.setString(4, replacement.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement echange pour " + uuid + " : " + e.getMessage());
            return null;
        }
        return replacement;
    }

    /** Liste des quetes EFFECTIVEMENT actives pour ce joueur : le pool complet, sauf les quetes
     * quotidiennes echangees (retirees) et avec les quetes de remplacement ajoutees a la place. */
    public List<QuestDefinition> getActiveQuestsForPlayer(UUID uuid) {
        String periodeCle = currentPeriodKey(QuestPeriod.DAILY);
        Set<String> exchangedAway = new HashSet<>();
        List<QuestDefinition> replacements = new ArrayList<>();
        String sql = "SELECT quest_originale_id, quest_remplacement_id FROM quest_echanges WHERE uuid = ? AND periode_cle = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, periodeCle);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    exchangedAway.add(rs.getString("quest_originale_id"));
                    QuestDefinition replacement = findQuestById(rs.getString("quest_remplacement_id"));
                    if (replacement != null) {
                        replacements.add(replacement);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture echanges actifs pour " + uuid + " : " + e.getMessage());
        }

        List<QuestDefinition> result = new ArrayList<>();
        for (QuestDefinition quest : getDailyQuests()) {
            if (!exchangedAway.contains(quest.id())) {
                result.add(quest);
            }
        }
        result.addAll(replacements);
        result.addAll(getWeeklyQuests());
        result.addAll(getPermanentQuests());
        return result;
    }

    // ---- Boost (xp/recompense doublee) ----

    public synchronized boolean isBoosted(UUID uuid, QuestDefinition quest) {
        String periodeCle = currentPeriodKey(quest.period());
        String sql = "SELECT 1 FROM quest_boosts WHERE uuid = ? AND quest_id = ? AND periode_cle = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, quest.id());
            statement.setString(3, periodeCle);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture boost pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    public synchronized void activateBoost(UUID uuid, QuestDefinition quest) {
        String periodeCle = currentPeriodKey(quest.period());
        String insert = "INSERT INTO quest_boosts (uuid, quest_id, periode_cle) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, quest.id());
            statement.setString(3, periodeCle);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur activation boost pour " + uuid + " : " + e.getMessage());
        }
    }

    // ---- Historique ----

    public synchronized void logCompletion(UUID uuid, String questNom, long xp) {
        String insert = "INSERT INTO quest_historique (uuid, quest_nom, xp, date_iso) VALUES (?, ?, ?, ?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, questNom);
            statement.setLong(3, xp);
            statement.setString(4, LocalDate.now().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur ecriture historique quetes pour " + uuid + " : " + e.getMessage());
        }
    }

    public record HistoryEntry(String questNom, long xp, String dateIso) {
    }

    public synchronized List<HistoryEntry> getRecentHistory(UUID uuid, int limit) {
        List<HistoryEntry> entries = new ArrayList<>();
        String sql = "SELECT quest_nom, xp, date_iso FROM quest_historique WHERE uuid = ? ORDER BY id DESC LIMIT ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new HistoryEntry(rs.getString("quest_nom"), rs.getLong("xp"), rs.getString("date_iso")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture historique quetes pour " + uuid + " : " + e.getMessage());
        }
        return entries;
    }

    // ---- Contrats (quetes optionnelles plus difficiles) ----

    public synchronized boolean isContractAccepted(UUID uuid, QuestDefinition quest) {
        String periodeCle = currentPeriodKey(quest.period());
        String sql = "SELECT 1 FROM quest_contrats WHERE uuid = ? AND quest_id = ? AND periode_cle = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, quest.id());
            statement.setString(3, periodeCle);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture contrat pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    public synchronized void acceptContract(UUID uuid, QuestDefinition quest) {
        String periodeCle = currentPeriodKey(quest.period());
        String insert = "INSERT INTO quest_contrats (uuid, quest_id, periode_cle) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, quest.id());
            statement.setString(3, periodeCle);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur acceptation contrat pour " + uuid + " : " + e.getMessage());
        }
    }

    // ---- Exploration (biomes differents visites cette semaine) ----

    /** Marque ce biome comme visite cette semaine pour ce joueur, et renvoie true si c'etait un
     * NOUVEAU biome (pas encore visite cette semaine). */
    public synchronized boolean markBiomeVisitedIfNew(UUID uuid, String biome) {
        String semaineCle = currentPeriodKey(QuestPeriod.WEEKLY);
        String sql = "INSERT INTO quest_biomes_vus (uuid, semaine_cle, biome) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, semaineCle);
            statement.setString(3, biome);
            int updated = statement.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement biome visite pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    /** Supprime toute la progression (quotidienne et hebdomadaire, toutes periodes confondues) d'un joueur. Commande admin. */
    public synchronized void resetAllProgress(UUID uuid) {
        Connection connection = database.getConnection();
        for (String table : new String[] {"quest_progression", "quest_streak", "quest_types_hebdo",
                "quest_echanges", "quest_boosts", "quest_contrats", "quest_biomes_vus"}) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?;")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur reinitialisation table '" + table + "' pour " + uuid + " : " + e.getMessage());
            }
        }
    }
}
