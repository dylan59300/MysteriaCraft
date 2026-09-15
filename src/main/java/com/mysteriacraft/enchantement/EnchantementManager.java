package com.mysteriacraft.enchantement;

import com.mysteriacraft.core.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Charge la configuration de la Table d'Enchantement Custom depuis enchantement.yml.
 */
public class EnchantementManager {

    private final Plugin plugin;
    private final ConfigManager enchantementConfig;

    private Material materiauTable = Material.SMITHING_TABLE;
    private String catalyseurId = "essence_enchantement";
    private int coutXpNiveaux = 10;
    private final List<EnchantementPool> pool = new ArrayList<>();

    public EnchantementManager(Plugin plugin, ConfigManager enchantementConfig) {
        this.plugin = plugin;
        this.enchantementConfig = enchantementConfig;
        loadConfig();
    }

    public void loadConfig() {
        pool.clear();
        Material configured = Material.matchMaterial(enchantementConfig.get().getString("materiau-table", "SMITHING_TABLE"));
        materiauTable = configured != null ? configured : Material.SMITHING_TABLE;
        catalyseurId = enchantementConfig.get().getString("catalyseur-id", "essence_enchantement");
        coutXpNiveaux = Math.max(0, enchantementConfig.get().getInt("cout-xp-niveaux", 10));

        List<?> poolRaw = enchantementConfig.get().getMapList("pool");
        for (Object raw : poolRaw) {
            if (!(raw instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            try {
                Enchantment enchantment = Enchantment.getByName(String.valueOf(map.get("enchantement")).toUpperCase());
                if (enchantment == null) {
                    plugin.getLogger().warning("Enchantement inconnu dans enchantement.yml : " + map.get("enchantement"));
                    continue;
                }
                int niveauMin = map.get("niveau-min") instanceof Number n ? n.intValue() : 1;
                int niveauMax = map.get("niveau-max") instanceof Number n ? n.intValue() : niveauMin;
                int poids = map.get("poids") instanceof Number n ? n.intValue() : 1;
                pool.add(new EnchantementPool(enchantment, Math.max(1, niveauMin), Math.max(niveauMin, niveauMax), Math.max(1, poids)));
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement d'une entree du pool d'enchantement : " + e.getMessage());
            }
        }
        plugin.getLogger().info(pool.size() + " enchantement(s) charge(s) dans le pool de la Table d'Enchantement Custom.");
    }

    public Material getMateriauTable() {
        return materiauTable;
    }

    public String getCatalyseurId() {
        return catalyseurId;
    }

    public int getCoutXpNiveaux() {
        return coutXpNiveaux;
    }

    public List<EnchantementPool> getPool() {
        return pool;
    }
}
