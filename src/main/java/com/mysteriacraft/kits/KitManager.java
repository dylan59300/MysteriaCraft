package com.mysteriacraft.kits;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Charge les kits depuis kits.yml et gere leur attribution (permission, cooldown persiste en SQLite).
 */
public class KitManager {

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
        String sql = "CREATE TABLE IF NOT EXISTS kits_cooldowns (" +
                "uuid TEXT NOT NULL, " +
                "kit_id TEXT NOT NULL, " +
                "derniere_utilisation INTEGER NOT NULL, " +
                "PRIMARY KEY (uuid, kit_id)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'kits_cooldowns' : " + e.getMessage());
        }
    }

    public void loadKits() {
        kits.clear();
        ConfigurationSection root = kitsConfig.get().getConfigurationSection("kits");
        if (root == null) {
            plugin.getLogger().warning("Aucun kit trouve dans kits.yml (section 'kits' manquante).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                Kit kit = parseKit(id, section);
                kits.put(id.toLowerCase(), kit);
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors du chargement du kit '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(kits.size() + " kit(s) charge(s) depuis kits.yml.");
    }

    private Kit parseKit(String id, ConfigurationSection section) {
        String displayName = section.getString("nom", id);
        Material icon = Material.matchMaterial(section.getString("icone", "CHEST"));
        if (icon == null) {
            icon = Material.CHEST;
        }
        int order = section.getInt("ordre", 0);
        List<String> lore = section.getStringList("lore");
        String permission = section.getString("permission", "");
        long cooldownSeconds = section.getLong("cooldown-heures", 24) * 3600L;

        List<ItemStack> items = new ArrayList<>();
        List<Map<?, ?>> itemMaps = section.getMapList("items");
        for (Map<?, ?> raw : itemMaps) {
            ItemStack item = parseItem(raw);
            if (item != null) {
                items.add(item);
            }
        }

        return new Kit(id, displayName, icon, order, lore, permission, cooldownSeconds, items);
    }

    @SuppressWarnings("unchecked")
    private ItemStack parseItem(Map<?, ?> raw) {
        Object materialRaw = raw.get("materiel");
        if (materialRaw == null) {
            return null;
        }
        Material material = Material.matchMaterial(String.valueOf(materialRaw));
        if (material == null) {
            plugin.getLogger().warning("Materiau inconnu dans kits.yml : " + materialRaw);
            return null;
        }

        int amount = raw.containsKey("quantite") ? (int) raw.get("quantite") : 1;
        ItemStack item = new ItemStack(material, Math.max(1, amount));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (raw.containsKey("nom")) {
                meta.setDisplayName(MessageManager.color(String.valueOf(raw.get("nom"))));
            }
            if (raw.containsKey("lore")) {
                Object loreRaw = raw.get("lore");
                if (loreRaw instanceof List<?> loreList) {
                    List<String> colored = new ArrayList<>();
                    for (Object line : loreList) {
                        colored.add(MessageManager.color(String.valueOf(line)));
                    }
                    meta.setLore(colored);
                }
            }
            item.setItemMeta(meta);
        }

        if (raw.containsKey("enchantements")) {
            Object enchantRaw = raw.get("enchantements");
            if (enchantRaw instanceof Map<?, ?> enchantMap) {
                for (Map.Entry<?, ?> entry : ((Map<Object, Object>) enchantMap).entrySet()) {
                    Enchantment enchantment = Registry.ENCHANTMENT.get(
                            NamespacedKey.minecraft(String.valueOf(entry.getKey()).toLowerCase()));
                    if (enchantment == null) {
                        plugin.getLogger().warning("Enchantement inconnu dans kits.yml : " + entry.getKey());
                        continue;
                    }
                    int level = Integer.parseInt(String.valueOf(entry.getValue()));
                    item.addUnsafeEnchantment(enchantment, level);
                }
            }
        }

        return item;
    }

    public Collection<Kit> getKitsSorted() {
        List<Kit> sorted = new ArrayList<>(kits.values());
        sorted.sort(Comparator.comparingInt(Kit::order));
        return sorted;
    }

    public Kit getKit(String id) {
        return kits.get(id.toLowerCase());
    }

    /** Lit le timestamp (millis) de la derniere utilisation d'un kit par un joueur. A appeler hors du thread principal. */
    public synchronized long getLastUsed(UUID uuid, String kitId) {
        String select = "SELECT derniere_utilisation FROM kits_cooldowns WHERE uuid = ? AND kit_id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kitId.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("derniere_utilisation");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture cooldown kit '" + kitId + "' pour " + uuid + " : " + e.getMessage());
        }
        return 0L;
    }

    /** Enregistre l'utilisation d'un kit maintenant. A appeler hors du thread principal. */
    public synchronized void markUsed(UUID uuid, String kitId) {
        String upsert = "INSERT INTO kits_cooldowns (uuid, kit_id, derniere_utilisation) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, kit_id) DO UPDATE SET derniere_utilisation = excluded.derniere_utilisation;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kitId.toLowerCase());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur enregistrement cooldown kit '" + kitId + "' pour " + uuid + " : " + e.getMessage());
        }
    }

    /** Copie profonde des items d'un kit (pour ne jamais donner les instances stockees en cache). */
    public List<ItemStack> cloneItems(Kit kit) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack item : kit.items()) {
            copy.add(item.clone());
        }
        return copy;
    }
}
