package com.mysteriacraft.customitems;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Charge les minerais/objets custom depuis custom_items.yml et fabrique leurs ItemStack
 * (item de base + CustomModelData + marque PersistentDataContainer). Le rendu visuel reel
 * necessite le resource pack fourni dans /resourcepack (voir son README).
 */
public class CustomItemManager {

    private final Plugin plugin;
    private final ConfigManager customItemsConfig;
    private final NamespacedKey itemKey;

    private final Map<String, CustomItemDefinition> items = new LinkedHashMap<>();
    /** Index inverse pour retrouver rapidement, a la casse d'un bloc, quels items custom peuvent en tomber. */
    private final Map<Material, List<CustomItemDefinition>> itemsBySourceOre = new HashMap<>();

    public CustomItemManager(Plugin plugin, ConfigManager customItemsConfig) {
        this.plugin = plugin;
        this.customItemsConfig = customItemsConfig;
        this.itemKey = new NamespacedKey(plugin, "custom-item");
        loadItems();
    }

    public void loadItems() {
        items.clear();
        itemsBySourceOre.clear();

        ConfigurationSection root = customItemsConfig.get().getConfigurationSection("items");
        if (root == null) {
            plugin.getLogger().warning("Aucun item custom trouve (section 'items' manquante).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                CustomItemDefinition definition = parseItem(id, section);
                items.put(id.toLowerCase(), definition);
                for (Material ore : definition.sourceOres()) {
                    itemsBySourceOre.computeIfAbsent(ore, k -> new ArrayList<>()).add(definition);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement item custom '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(items.size() + " item(s) custom charge(s) depuis custom_items.yml.");
    }

    private CustomItemDefinition parseItem(String id, ConfigurationSection section) {
        String displayName = section.getString("nom", id);
        List<String> lore = section.getStringList("lore");
        Material baseItem = Material.matchMaterial(section.getString("item-de-base", "IRON_INGOT"));
        if (baseItem == null) {
            baseItem = Material.IRON_INGOT;
        }
        int customModelData = section.getInt("custom-model-data", 0);
        double dropChance = section.getDouble("chance-drop", 0);
        double sellPrice = section.getDouble("prix-vente", 0);

        List<Material> sourceOres = new ArrayList<>();
        for (String materialName : section.getStringList("minerais-source")) {
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                sourceOres.add(material);
            } else {
                plugin.getLogger().warning("Materiau source inconnu pour l'item custom '" + id + "' : " + materialName);
            }
        }

        return new CustomItemDefinition(id, displayName, lore, baseItem, customModelData, sourceOres, dropChance, sellPrice);
    }

    public List<CustomItemDefinition> getItemsSorted() {
        return new ArrayList<>(items.values());
    }

    public CustomItemDefinition getItem(String id) {
        return id == null ? null : items.get(id.toLowerCase());
    }

    /** Items custom pouvant tomber de ce type de bloc (liste vide si aucun). */
    public List<CustomItemDefinition> getItemsForOre(Material oreMaterial) {
        return itemsBySourceOre.getOrDefault(oreMaterial, List.of());
    }

    public ItemStack createItem(CustomItemDefinition definition) {
        return createItem(definition, 1);
    }

    public ItemStack createItem(CustomItemDefinition definition, int amount) {
        ItemStack item = new ItemStack(definition.baseItem(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.color(definition.displayName()));
            List<String> coloredLore = new ArrayList<>();
            for (String line : definition.lore()) {
                coloredLore.add(MessageManager.color(line));
            }
            meta.setLore(coloredLore);
            meta.setCustomModelData(definition.customModelData());
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, definition.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Renvoie l'id de l'item custom marque sur cet ItemStack, ou null si ce n'en est pas un. */
    public String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }
}
