package com.mysteriacraft.runes;

import com.mysteriacraft.core.config.ConfigManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Charge les runes depuis runes.yml : chacune est liee a un item custom consommable et, une fois
 * gravee sur une arme, declenche un effet a chance au moment de toucher un adversaire (voir
 * RuneListener).
 */
public class RuneManager {

    public enum EffetType {
        FEU, GLACE, VIE
    }

    public record RuneType(String id, String nom, EffetType effet, double chancePourcent, double valeur) {
    }

    private final Plugin plugin;
    private final ConfigManager runesConfig;
    private final NamespacedKey runeKey;
    private final Map<String, RuneType> types = new LinkedHashMap<>();
    private final Map<String, String> itemIdToType = new LinkedHashMap<>();

    public RuneManager(Plugin plugin, ConfigManager runesConfig) {
        this.plugin = plugin;
        this.runesConfig = runesConfig;
        this.runeKey = new NamespacedKey(plugin, "rune-gravee");
        load();
    }

    public void load() {
        types.clear();
        itemIdToType.clear();
        ConfigurationSection root = runesConfig.get().getConfigurationSection("runes");
        if (root == null) {
            plugin.getLogger().warning("Aucune rune trouvee (section 'runes' manquante).");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String nom = section.getString("nom", id);
            EffetType effet;
            try {
                effet = EffetType.valueOf(section.getString("effet", "FEU").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Effet de rune invalide pour '" + id + "' : " + section.getString("effet"));
                continue;
            }
            double chance = section.getDouble("chance-pourcent", 15);
            double valeur = section.getDouble("valeur", 1);
            String itemId = section.getString("item-id", id);
            types.put(id.toLowerCase(), new RuneType(id.toLowerCase(), nom, effet, chance, valeur));
            itemIdToType.put(itemId.toLowerCase(), id.toLowerCase());
        }
        plugin.getLogger().info(types.size() + " rune(s) chargee(s).");
    }

    public RuneType getTypeFromItemId(String customItemId) {
        if (customItemId == null) {
            return null;
        }
        String typeId = itemIdToType.get(customItemId.toLowerCase());
        return typeId == null ? null : types.get(typeId);
    }

    public RuneType getType(String id) {
        return id == null ? null : types.get(id.toLowerCase());
    }

    public NamespacedKey getRuneKey() {
        return runeKey;
    }
}
