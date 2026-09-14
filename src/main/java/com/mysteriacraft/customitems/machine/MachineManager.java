package com.mysteriacraft.customitems.machine;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Charge la configuration de la Machine a Transformation (bloc, minerais acceptes -> famille
 * de Lucky Block cible, chance de reussite) et fabrique/marque son bloc.
 */
public class MachineManager {

    private final Plugin plugin;
    private final ConfigManager customItemsConfig;
    private final NamespacedKey machineKey;

    private Material blockMaterial = Material.IRON_BLOCK;
    private double successChance = 20.0;
    private final Map<Material, String> acceptedOres = new HashMap<>();

    public MachineManager(Plugin plugin, ConfigManager customItemsConfig) {
        this.plugin = plugin;
        this.customItemsConfig = customItemsConfig;
        this.machineKey = new NamespacedKey(plugin, "machine-transformation");
        loadConfig();
    }

    public void loadConfig() {
        acceptedOres.clear();
        ConfigurationSection section = customItemsConfig.get().getConfigurationSection("machine-transformation");
        if (section == null) {
            plugin.getLogger().warning("Section 'machine-transformation' manquante dans custom_items.yml.");
            return;
        }

        Material material = Material.matchMaterial(section.getString("bloc", "IRON_BLOCK"));
        blockMaterial = material != null ? material : Material.IRON_BLOCK;
        successChance = section.getDouble("chance-reussite", 20.0);

        ConfigurationSection ores = section.getConfigurationSection("minerais");
        if (ores != null) {
            for (String materialName : ores.getKeys(false)) {
                Material ore = Material.matchMaterial(materialName);
                if (ore == null) {
                    plugin.getLogger().warning("Materiau inconnu dans machine-transformation.minerais : " + materialName);
                    continue;
                }
                acceptedOres.put(ore, ores.getString(materialName));
            }
        }
        plugin.getLogger().info("Machine a Transformation : " + acceptedOres.size() + " minerai(s) accepte(s), "
                + successChance + "% de reussite.");
    }

    public Material getBlockMaterial() {
        return blockMaterial;
    }

    public double getSuccessChance() {
        return successChance;
    }

    /** Famille de Lucky Block cible pour ce minerai, ou null si non accepte par la machine. */
    public String getTargetFamily(Material ore) {
        return acceptedOres.get(ore);
    }

    public boolean isAccepted(Material material) {
        return acceptedOres.containsKey(material);
    }

    public List<Material> getAcceptedMaterials() {
        return List.copyOf(acceptedOres.keySet());
    }

    public ItemStack createMachineItem() {
        return createMachineItem(1);
    }

    public ItemStack createMachineItem(int amount) {
        ItemStack item = new ItemStack(blockMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.color("&b&lMachine a Transformation"));
            meta.setLore(List.of(
                    MessageManager.color("&7Clic-droit avec un minerai en main"),
                    MessageManager.color("&7pour tenter de le transformer"),
                    MessageManager.color("&7en Lucky Block."),
                    MessageManager.color("&7Chance de reussite : &e" + (int) successChance + "%")
            ));
            meta.getPersistentDataContainer().set(machineKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Marque un bloc pose comme etant la Machine a Transformation. */
    public void tagBlock(Block block) {
        block.getPersistentDataContainer().set(machineKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isMachineBlock(Block block) {
        return block.getPersistentDataContainer().has(machineKey, PersistentDataType.BYTE);
    }

    public boolean isMachineItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(machineKey, PersistentDataType.BYTE);
    }
}
