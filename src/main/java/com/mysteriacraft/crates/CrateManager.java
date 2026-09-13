package com.mysteriacraft.crates;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Charge les caisses depuis crates.yml et gere les cles virtuelles (persistees en SQLite,
 * aucun objet physique requis) ainsi que le tirage pondere des recompenses.
 */
public class CrateManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager cratesConfig;

    private final Map<String, Crate> crates = new LinkedHashMap<>();

    public CrateManager(Plugin plugin, Database database, ConfigManager cratesConfig) {
        this.plugin = plugin;
        this.database = database;
        this.cratesConfig = cratesConfig;
        createTables();
        loadCrates();
    }

    private void createTables() {
        String keysTable = "CREATE TABLE IF NOT EXISTS crate_keys (" +
                "uuid TEXT NOT NULL, " +
                "crate_id TEXT NOT NULL, " +
                "quantite INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, crate_id)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(keysTable)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'crate_keys' : " + e.getMessage());
        }
    }

    public void loadCrates() {
        crates.clear();
        ConfigurationSection root = cratesConfig.get().getConfigurationSection("crates");
        if (root == null) {
            plugin.getLogger().warning("Aucune caisse trouvee dans crates.yml (section 'crates' manquante).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                Crate crate = parseCrate(id, section);
                crates.put(id.toLowerCase(), crate);
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors du chargement de la caisse '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(crates.size() + " caisse(s) chargee(s) depuis crates.yml.");
    }

    private Crate parseCrate(String id, ConfigurationSection section) {
        String displayName = section.getString("nom", id);
        Material icon = Material.matchMaterial(section.getString("icone", "CHEST"));
        if (icon == null) {
            icon = Material.CHEST;
        }
        int order = section.getInt("ordre", 0);
        List<String> lore = section.getStringList("lore");
        String permission = section.getString("permission", "");
        CrateAnimationType animation = CrateAnimationType.fromString(section.getString("animation", "INSTANT"));

        List<CrateReward> rewards = new ArrayList<>();
        List<Map<?, ?>> lootMaps = section.getMapList("loot");
        for (Map<?, ?> raw : lootMaps) {
            CrateReward reward = parseReward(raw);
            if (reward != null) {
                rewards.add(reward);
            }
        }

        return new Crate(id, displayName, icon, order, lore, permission, animation, rewards);
    }

    private CrateReward parseReward(Map<?, ?> raw) {
        String id = String.valueOf(raw.getOrDefault("id", "recompense"));
        RewardType type = raw.containsKey("type") && "ECONOMIE".equalsIgnoreCase(String.valueOf(raw.get("type")))
                ? RewardType.ECONOMIE : RewardType.ITEM;
        double chance = raw.containsKey("chance") ? Double.parseDouble(String.valueOf(raw.get("chance"))) : 1.0;
        Rarity rarity = Rarity.fromString(raw.containsKey("rarete") ? String.valueOf(raw.get("rarete")) : null);

        if (type == RewardType.ECONOMIE) {
            double amount = raw.containsKey("montant") ? Double.parseDouble(String.valueOf(raw.get("montant"))) : 0;
            Material iconMaterial = Material.matchMaterial(String.valueOf(raw.getOrDefault("icone", "GOLD_INGOT")));
            if (iconMaterial == null) {
                iconMaterial = Material.GOLD_INGOT;
            }
            String displayName = raw.containsKey("nom") ? String.valueOf(raw.get("nom"))
                    : (rarity.color() + "" + amount + "$");
            ItemStack displayIcon = new ItemBuilder(iconMaterial)
                    .name(displayName)
                    .lore(List.of("&7Recompense : &e" + amount + "$"))
                    .build();
            return new CrateReward(id, RewardType.ECONOMIE, null, amount, chance, rarity, displayName, displayIcon);
        }

        Object materialRaw = raw.get("materiel");
        if (materialRaw == null) {
            plugin.getLogger().warning("Recompense sans 'materiel' ignoree dans crates.yml (id=" + id + ").");
            return null;
        }
        Material material = Material.matchMaterial(String.valueOf(materialRaw));
        if (material == null) {
            plugin.getLogger().warning("Materiau inconnu dans crates.yml : " + materialRaw);
            return null;
        }
        int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
        ItemStack item = new ItemStack(material, Math.max(1, amount));

        String displayName = raw.containsKey("nom") ? String.valueOf(raw.get("nom"))
                : rarity.color() + item.getType().name().replace('_', ' ');
        ItemStack displayIcon = new ItemBuilder(material, Math.max(1, amount))
                .name(displayName)
                .build();

        return new CrateReward(id, RewardType.ITEM, item, 0, chance, rarity, displayName, displayIcon);
    }

    public List<Crate> getCratesSorted() {
        List<Crate> sorted = new ArrayList<>(crates.values());
        sorted.sort(Comparator.comparingInt(Crate::order));
        return sorted;
    }

    public Crate getCrate(String id) {
        return crates.get(id.toLowerCase());
    }

    /** Tirage pondere d'une recompense parmi celles de la caisse, selon leur "chance" relative. */
    public CrateReward pickReward(Crate crate) {
        double totalWeight = crate.totalWeight();
        if (totalWeight <= 0 || crate.rewards().isEmpty()) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (CrateReward reward : crate.rewards()) {
            cumulative += reward.chance();
            if (roll < cumulative) {
                return reward;
            }
        }
        return crate.rewards().get(crate.rewards().size() - 1);
    }

    // ---- Cles virtuelles ----

    public synchronized int getKeyCount(UUID uuid, String crateId) {
        String select = "SELECT quantite FROM crate_keys WHERE uuid = ? AND crate_id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, crateId.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("quantite");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture cles caisse '" + crateId + "' pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    /** Ajoute (ou retire si negatif) des cles virtuelles, jamais sous 0. Renvoie la nouvelle quantite. */
    public synchronized int addKeys(UUID uuid, String crateId, int delta) {
        int current = getKeyCount(uuid, crateId);
        int updated = Math.max(0, current + delta);

        String upsert = "INSERT INTO crate_keys (uuid, crate_id, quantite) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, crate_id) DO UPDATE SET quantite = excluded.quantite;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, crateId.toLowerCase());
            statement.setInt(3, updated);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour cles caisse '" + crateId + "' pour " + uuid + " : " + e.getMessage());
        }
        return updated;
    }

    /** Consomme une cle si le joueur en possede au moins une. Renvoie true si consommee. */
    public synchronized boolean consumeKey(UUID uuid, String crateId) {
        int current = getKeyCount(uuid, crateId);
        if (current <= 0) {
            return false;
        }
        addKeys(uuid, crateId, -1);
        return true;
    }

    public synchronized int setKeys(UUID uuid, String crateId, int amount) {
        return addKeys(uuid, crateId, amount - getKeyCount(uuid, crateId));
    }
}
