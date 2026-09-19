package com.mysteriacraft.recyclage;

import com.mysteriacraft.core.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Charge recyclage.yml : pour chaque item custom recyclable, la liste de materiaux vanilla rendus. */
public class RecyclageManager {

    public record MaterialReward(Material material, int amount) {
    }

    private final Plugin plugin;
    private final ConfigManager recyclageConfig;
    private final Map<String, List<MaterialReward>> recettes = new LinkedHashMap<>();

    public RecyclageManager(Plugin plugin, ConfigManager recyclageConfig) {
        this.plugin = plugin;
        this.recyclageConfig = recyclageConfig;
        load();
    }

    public void load() {
        recettes.clear();
        ConfigurationSection root = recyclageConfig.get().getConfigurationSection("items");
        if (root == null) {
            plugin.getLogger().warning("Aucun item recyclable trouve (section 'items' manquante).");
            return;
        }
        for (String itemId : root.getKeys(false)) {
            List<MaterialReward> rewards = new ArrayList<>();
            for (Map<?, ?> raw : root.getMapList(itemId + ".recompenses")) {
                Object materielRaw = raw.get("materiel");
                if (materielRaw == null) {
                    continue;
                }
                Material material = Material.matchMaterial(String.valueOf(materielRaw));
                if (material == null) {
                    continue;
                }
                int quantite = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
                rewards.add(new MaterialReward(material, Math.max(1, quantite)));
            }
            if (!rewards.isEmpty()) {
                recettes.put(itemId.toLowerCase(), rewards);
            }
        }
        plugin.getLogger().info(recettes.size() + " item(s) recyclable(s) charge(s).");
    }

    public List<MaterialReward> getRewards(String customItemId) {
        return customItemId == null ? List.of() : recettes.getOrDefault(customItemId.toLowerCase(), List.of());
    }

    public boolean isRecyclable(String customItemId) {
        return !getRewards(customItemId).isEmpty();
    }
}
