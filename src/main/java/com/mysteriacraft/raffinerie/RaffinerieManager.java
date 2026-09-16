package com.mysteriacraft.raffinerie;

import com.mysteriacraft.core.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Charge la Raffinerie depuis raffinerie.yml : un bloc unique (repere par son materiau, sans
 * suivi par position ni SQLite : n'importe quel bloc pose de ce materiau agit comme une
 * Raffinerie) qui convertit un minerai brut en son lingot (ou equivalent) au clic-droit, avec une
 * chance d'obtenir un lingot bonus.
 */
public class RaffinerieManager {

    private final Plugin plugin;
    private final ConfigManager raffinerieConfig;
    private Material blockMaterial = Material.SMOKER;
    private double chanceBonusPourcent = 15;
    private final Map<Material, Material> recettes = new LinkedHashMap<>();

    public RaffinerieManager(Plugin plugin, ConfigManager raffinerieConfig) {
        this.plugin = plugin;
        this.raffinerieConfig = raffinerieConfig;
        load();
    }

    public void load() {
        recettes.clear();
        Material material = Material.matchMaterial(raffinerieConfig.get().getString("bloc", "SMOKER"));
        blockMaterial = material != null ? material : Material.SMOKER;
        chanceBonusPourcent = raffinerieConfig.get().getDouble("chance-bonus-pourcent", 15);

        ConfigurationSection section = raffinerieConfig.get().getConfigurationSection("recettes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material input = Material.matchMaterial(key);
                Material output = Material.matchMaterial(section.getString(key, ""));
                if (input == null || output == null) {
                    plugin.getLogger().warning("Recette de raffinerie invalide : " + key + " -> " + section.getString(key));
                    continue;
                }
                recettes.put(input, output);
            }
        }
        plugin.getLogger().info(recettes.size() + " recette(s) de raffinerie chargee(s).");
    }

    public boolean isRaffinerieBlock(Material material) {
        return material == blockMaterial;
    }

    public Material getOutput(Material input) {
        return recettes.get(input);
    }

    public double getChanceBonusPourcent() {
        return chanceBonusPourcent;
    }
}
