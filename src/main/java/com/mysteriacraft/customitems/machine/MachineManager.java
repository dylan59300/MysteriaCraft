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
 * de Lucky Block cible, chance de reussite, carburant, cooldown) et fabrique/marque son bloc.
 * L'etat de chaque machine posee (charges de carburant restantes, horodatage de derniere
 * utilisation) est stocke directement sur le bloc via PersistentDataContainer (extension Paper).
 */
public class MachineManager {

    private final Plugin plugin;
    private final ConfigManager customItemsConfig;
    private final NamespacedKey machineKey;
    private final NamespacedKey fuelKey;
    private final NamespacedKey lastUseKey;

    private Material blockMaterial = Material.IRON_BLOCK;
    private double successChance = 20.0;
    private long cooldownSeconds = 60L;
    private String fuelItemId = "carburant";
    private int chargesPerFuel = 5;
    private final Map<Material, String> acceptedOres = new HashMap<>();

    public MachineManager(Plugin plugin, ConfigManager customItemsConfig) {
        this.plugin = plugin;
        this.customItemsConfig = customItemsConfig;
        this.machineKey = new NamespacedKey(plugin, "machine-transformation");
        this.fuelKey = new NamespacedKey(plugin, "machine-carburant");
        this.lastUseKey = new NamespacedKey(plugin, "machine-derniere-utilisation");
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
        cooldownSeconds = section.getLong("cooldown-secondes", 60L);
        fuelItemId = section.getString("carburant-item-id", "carburant");
        chargesPerFuel = Math.max(1, section.getInt("charges-par-carburant", 5));

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
                + successChance + "% de reussite, cooldown " + cooldownSeconds + "s, carburant '" + fuelItemId + "'.");
    }

    public Material getBlockMaterial() {
        return blockMaterial;
    }

    public double getSuccessChance() {
        return successChance;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public String getFuelItemId() {
        return fuelItemId;
    }

    public int getChargesPerFuel() {
        return chargesPerFuel;
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
                    MessageManager.color("&7Chance de reussite : &e" + (int) successChance + "%"),
                    MessageManager.color("&7Necessite du carburant pour fonctionner."),
                    MessageManager.color("&7Clic a vide pour voir son etat.")
            ));
            meta.getPersistentDataContainer().set(machineKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Marque un bloc pose comme etant la Machine a Transformation (charges/cooldown a 0 par defaut). */
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

    // ---- Etat de la machine (carburant, cooldown), stocke sur le bloc ----

    public int getFuel(Block block) {
        Integer value = block.getPersistentDataContainer().get(fuelKey, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    public void setFuel(Block block, int amount) {
        block.getPersistentDataContainer().set(fuelKey, PersistentDataType.INTEGER, Math.max(0, amount));
    }

    /** Ajoute des charges de carburant au bloc. Renvoie le nouveau total. */
    public int addFuel(Block block, int amount) {
        int newTotal = getFuel(block) + amount;
        setFuel(block, newTotal);
        return newTotal;
    }

    public void consumeCharge(Block block) {
        setFuel(block, getFuel(block) - 1);
    }

    public long getLastUseMillis(Block block) {
        Long value = block.getPersistentDataContainer().get(lastUseKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    public void markUsedNow(Block block) {
        block.getPersistentDataContainer().set(lastUseKey, PersistentDataType.LONG, System.currentTimeMillis());
    }

    /** Millisecondes restantes avant la fin du cooldown (0 ou negatif = disponible). */
    public long getRemainingCooldownMillis(Block block) {
        long lastUse = getLastUseMillis(block);
        if (lastUse == 0L) {
            return 0L;
        }
        return (lastUse + cooldownSeconds * 1000L) - System.currentTimeMillis();
    }
}
