package com.mysteriacraft.battlepass;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Missions quotidiennes integrees au BattlePass (comme le vrai plugin BattlePass), independantes
 * du module Quetes : chaque jour, quelques missions sont tirees au hasard (mais figees pour la
 * journee, par joueur) parmi des modeles configures dans battlepass.yml, et donnent de l'xp
 * BattlePass directement des qu'elles sont terminees. Voir /battlepass missions.
 */
public class BattlePassMissionManager {

    public enum MissionType { MINER, TUER, PECHER }

    public record MissionTemplate(MissionType type, String cible, int quantiteMin, int quantiteMax) {
    }

    public record Mission(int index, MissionType type, String cible, int objectif, int progression,
                           int xpRecompense, boolean terminee) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager battlepassConfig;

    private final List<MissionTemplate> templates = new ArrayList<>();
    private int nombreParJour = 3;
    private int xpRecompenseMin = 30;
    private int xpRecompenseMax = 80;

    public BattlePassMissionManager(Plugin plugin, Database database, ConfigManager battlepassConfig) {
        this.plugin = plugin;
        this.database = database;
        this.battlepassConfig = battlepassConfig;
        createTable();
        loadTemplates();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS battlepass_missions (" +
                "uuid TEXT NOT NULL, jour TEXT NOT NULL, index_mission INTEGER NOT NULL, " +
                "type TEXT NOT NULL, cible TEXT NOT NULL, objectif INTEGER NOT NULL, " +
                "progression INTEGER NOT NULL DEFAULT 0, xp_recompense INTEGER NOT NULL, " +
                "terminee INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, jour, index_mission));";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table battlepass_missions : " + e.getMessage());
        }
    }

    public void loadTemplates() {
        templates.clear();
        ConfigurationSection section = battlepassConfig.get().getConfigurationSection("missions-quotidiennes");
        if (section == null) {
            return;
        }
        nombreParJour = Math.max(1, section.getInt("nombre-par-jour", 3));
        xpRecompenseMin = Math.max(0, section.getInt("xp-recompense-min", 30));
        xpRecompenseMax = Math.max(xpRecompenseMin, section.getInt("xp-recompense-max", 80));

        List<?> modelesRaw = section.getMapList("modeles");
        for (Object raw : modelesRaw) {
            if (!(raw instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            try {
                MissionType type = MissionType.valueOf(String.valueOf(map.get("type")));
                String cible = type == MissionType.PECHER ? "POISSON" :
                        String.valueOf(type == MissionType.TUER ? map.get("entite") : map.get("materiel"));
                int quantiteMin = map.get("quantite-min") instanceof Number n ? n.intValue() : 1;
                int quantiteMax = map.get("quantite-max") instanceof Number n ? n.intValue() : quantiteMin;
                templates.add(new MissionTemplate(type, cible, Math.max(1, quantiteMin), Math.max(quantiteMin, quantiteMax)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Modele de mission BattlePass invalide dans battlepass.yml : " + map);
            }
        }
    }

    /** Renvoie les missions du jour pour ce joueur, en les generant (tirage deterministe par
     * joueur+jour) si elles n'existent pas encore. A appeler hors thread principal. */
    public synchronized List<Mission> getOrGenerateDailyMissions(UUID uuid) {
        String jour = LocalDate.now().toString();
        List<Mission> existing = loadMissions(uuid, jour);
        if (!existing.isEmpty() || templates.isEmpty()) {
            return existing;
        }

        Random random = new Random(uuid.hashCode() * 31L ^ LocalDate.now().toEpochDay());
        List<MissionTemplate> pool = new ArrayList<>(templates);
        java.util.Collections.shuffle(pool, random);
        int count = Math.min(nombreParJour, pool.size());

        Connection connection = database.getConnection();
        String insert = "INSERT INTO battlepass_missions (uuid, jour, index_mission, type, cible, objectif, xp_recompense) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            for (int i = 0; i < count; i++) {
                MissionTemplate template = pool.get(i);
                int objectif = template.quantiteMin() + random.nextInt(template.quantiteMax() - template.quantiteMin() + 1);
                int xp = xpRecompenseMin + random.nextInt(xpRecompenseMax - xpRecompenseMin + 1);
                statement.setString(1, uuid.toString());
                statement.setString(2, jour);
                statement.setInt(3, i);
                statement.setString(4, template.type().name());
                statement.setString(5, template.cible());
                statement.setInt(6, objectif);
                statement.setInt(7, xp);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur generation des missions BattlePass pour " + uuid + " : " + e.getMessage());
        }
        return loadMissions(uuid, jour);
    }

    private List<Mission> loadMissions(UUID uuid, String jour) {
        List<Mission> missions = new ArrayList<>();
        String select = "SELECT index_mission, type, cible, objectif, progression, xp_recompense, terminee " +
                "FROM battlepass_missions WHERE uuid = ? AND jour = ? ORDER BY index_mission;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, jour);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    missions.add(new Mission(
                            rs.getInt("index_mission"),
                            MissionType.valueOf(rs.getString("type")),
                            rs.getString("cible"),
                            rs.getInt("objectif"),
                            rs.getInt("progression"),
                            rs.getInt("xp_recompense"),
                            rs.getInt("terminee") != 0));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture des missions BattlePass pour " + uuid + " : " + e.getMessage());
        }
        return missions;
    }

    /** Fait progresser les missions du jour de ce type/cible pour ce joueur, et renvoie celles qui
     * viennent d'etre terminees par cet appel (a recompenser par l'appelant). A appeler hors thread
     * principal. */
    public synchronized List<Mission> recordProgress(UUID uuid, MissionType type, String cible, int amount) {
        List<Mission> completedNow = new ArrayList<>();
        String jour = LocalDate.now().toString();
        List<Mission> missions = loadMissions(uuid, jour);
        if (missions.isEmpty()) {
            return completedNow;
        }

        Connection connection = database.getConnection();
        String update = "UPDATE battlepass_missions SET progression = ?, terminee = ? " +
                "WHERE uuid = ? AND jour = ? AND index_mission = ?;";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            for (Mission mission : missions) {
                if (mission.terminee() || mission.type() != type || !mission.cible().equalsIgnoreCase(cible)) {
                    continue;
                }
                int newProgress = Math.min(mission.objectif(), mission.progression() + amount);
                boolean nowComplete = newProgress >= mission.objectif();
                statement.setInt(1, newProgress);
                statement.setInt(2, nowComplete ? 1 : 0);
                statement.setString(3, uuid.toString());
                statement.setString(4, jour);
                statement.setInt(5, mission.index());
                statement.executeUpdate();
                if (nowComplete) {
                    completedNow.add(new Mission(mission.index(), mission.type(), mission.cible(),
                            mission.objectif(), newProgress, mission.xpRecompense(), true));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur progression des missions BattlePass pour " + uuid + " : " + e.getMessage());
        }
        return completedNow;
    }
}
