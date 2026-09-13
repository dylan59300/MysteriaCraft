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
import java.util.List;
import java.util.Locale;
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

    public QuestManager(Plugin plugin, Database database, ConfigManager questsConfig) {
        this.plugin = plugin;
        this.database = database;
        this.questsConfig = questsConfig;
        createTable();
        loadQuests();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS quest_progression (" +
                "uuid TEXT NOT NULL, " +
                "quest_id TEXT NOT NULL, " +
                "periode_cle TEXT NOT NULL, " +
                "progression INTEGER NOT NULL DEFAULT 0, " +
                "reclame INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, quest_id, periode_cle)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'quest_progression' : " + e.getMessage());
        }
    }

    public void loadQuests() {
        dailyQuests.clear();
        weeklyQuests.clear();
        loadSection("quotidiennes", QuestPeriod.DAILY, dailyQuests);
        loadSection("hebdomadaires", QuestPeriod.WEEKLY, weeklyQuests);
        plugin.getLogger().info((dailyQuests.size() + weeklyQuests.size()) + " quete(s) chargee(s) depuis quests.yml.");
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
        ItemStack icon = new ItemBuilder(iconMaterial).name(displayName).build();

        QuestType type = QuestType.fromString(section.getString("type", "BREAK_BLOCK"));
        String target = section.getString("cible", null);
        int objective = Math.max(1, section.getInt("objectif", 1));
        long xp = section.getLong("xp", 0);
        Reward reward = RewardParser.parse(section.getConfigurationSection("recompense"));

        return new QuestDefinition(id, displayName, icon, type, target, objective, xp, reward, period);
    }

    public List<QuestDefinition> getDailyQuests() {
        return dailyQuests;
    }

    public List<QuestDefinition> getWeeklyQuests() {
        return weeklyQuests;
    }

    public List<QuestDefinition> getAllQuests() {
        List<QuestDefinition> all = new ArrayList<>(dailyQuests);
        all.addAll(weeklyQuests);
        return all;
    }

    /** Cle de periode courante (change automatiquement a minuit / au changement de semaine ISO). */
    public String currentPeriodKey(QuestPeriod period) {
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

    /** Supprime toute la progression (quotidienne et hebdomadaire, toutes periodes confondues) d'un joueur. Commande admin. */
    public synchronized void resetAllProgress(UUID uuid) {
        String delete = "DELETE FROM quest_progression WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(delete)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur reinitialisation progression quetes pour " + uuid + " : " + e.getMessage());
        }
    }
}
