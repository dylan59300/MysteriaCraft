package com.mysteriacraft.kit;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Charge les kits depuis kits.yml (chacun est une liste de Reward, voir com.mysteriacraft.core.reward,
 * n'importe quel type de recompense fonctionne) et suit en SQLite la derniere date de recuperation
 * de chaque joueur pour chaque kit, afin d'appliquer le cooldown (ou l'unicite) configure.
 */
public class KitManager {

    /** Un kit : ce qu'il donne (liste de recompenses), son cooldown (0 = illimite) et s'il n'est
     * recuperable qu'une seule fois par joueur (auquel cas le cooldown est ignore). */
    public record Kit(String id, String displayName, ItemStack icon, List<Reward> rewards,
                       long cooldownSeconds, boolean unique, String permission) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager kitsConfig;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(Plugin plugin, Database database, ConfigManager kitsConfig) {
        this.plugin = plugin;
        this.database = database;
        this.kitsConfig = kitsConfig;
        createTable();
        loadKits();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS kit_claims (" +
                "uuid TEXT NOT NULL, " +
                "kit_id TEXT NOT NULL, " +
                "derniere_recuperation_millis INTEGER NOT NULL, " +
                "PRIMARY KEY (uuid, kit_id)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'kit_claims' : " + e.getMessage());
        }
    }

    public void loadKits() {
        kits.clear();
        ConfigurationSection root = kitsConfig.get().getConfigurationSection("kits");
        if (root == null) {
            plugin.getLogger().warning("Aucun kit trouve (section 'kits' manquante).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                kits.put(id.toLowerCase(), parseKit(id, section));
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement kit '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(kits.size() + " kit(s) charge(s).");
    }

    private Kit parseKit(String id, ConfigurationSection section) {
        String displayName = section.getString("nom", id);
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "CHEST"));
        if (iconMaterial == null) {
            iconMaterial = Material.CHEST;
        }
        ItemStack icon = new ItemBuilder(iconMaterial).name(displayName).build();

        List<Reward> rewards = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("recompenses")) {
            @SuppressWarnings("unchecked")
            ConfigurationSection rewardSection = mapToSection((Map<String, Object>) raw);
            Reward reward = RewardParser.parse(rewardSection);
            if (reward != null) {
                rewards.add(reward);
            }
        }

        long cooldownSeconds = Math.max(0, section.getLong("cooldown-secondes", 0));
        boolean unique = section.getBoolean("unique", false);
        String permission = section.getString("permission", null);
        return new Kit(id.toLowerCase(), displayName, icon, rewards, cooldownSeconds, unique, permission);
    }

    private ConfigurationSection mapToSection(Map<String, Object> map) {
        MemoryConfiguration memoryConfig = new MemoryConfiguration();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            memoryConfig.set(entry.getKey(), entry.getValue());
        }
        return memoryConfig;
    }

    public List<Kit> getKitsSorted() {
        return new ArrayList<>(kits.values());
    }

    public Kit getKit(String id) {
        return id == null ? null : kits.get(id.toLowerCase());
    }

    /** Dernier instant (millis) auquel ce joueur a recupere ce kit, ou null si jamais. Requete
     * synchrone : a appeler hors du thread principal. */
    public Long getLastClaimMillis(UUID uuid, String kitId) {
        String select = "SELECT derniere_recuperation_millis FROM kit_claims WHERE uuid = ? AND kit_id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kitId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("derniere_recuperation_millis");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture derniere recuperation du kit '" + kitId + "' pour " + uuid + " : " + e.getMessage());
        }
        return null;
    }

    /** Renvoie le temps restant (millis) avant de pouvoir recuperer a nouveau ce kit (0 si
     * disponible maintenant), ou -1 si le kit est "unique" et deja recupere (jamais disponible a
     * nouveau). Requete synchrone : a appeler hors du thread principal. */
    public long getRemainingCooldownMillis(UUID uuid, Kit kit) {
        Long last = getLastClaimMillis(uuid, kit.id());
        if (last == null) {
            return 0;
        }
        if (kit.unique()) {
            return -1;
        }
        if (kit.cooldownSeconds() <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - last;
        long total = kit.cooldownSeconds() * 1000L;
        return Math.max(0, total - elapsed);
    }

    /** Enregistre la recuperation de ce kit maintenant. A appeler de maniere asynchrone. */
    public void markClaimed(UUID uuid, String kitId) {
        String upsert = "INSERT INTO kit_claims (uuid, kit_id, derniere_recuperation_millis) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, kit_id) DO UPDATE SET derniere_recuperation_millis = excluded.derniere_recuperation_millis;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kitId);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur sauvegarde recuperation du kit '" + kitId + "' pour " + uuid + " : " + e.getMessage());
        }
    }
}
