package com.mysteriacraft.customitems.machine;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charge la configuration de la Machine a Transformation (tiers structurels, minerais acceptes
 * -> famille de Lucky Block cible, types de carburant, amelioration) et fabrique/marque son bloc.
 * L'etat de chaque machine posee (tier actuel, charges de carburant restantes, cooldown actif,
 * bonus de reussite, horodatage de derniere utilisation) est stocke directement sur le bloc via
 * PersistentDataContainer (extension Paper).
 */
public class MachineManager {

    /** Un type de carburant utilisable : charges qu'il apporte et cooldown qu'il impose tant qu'il est actif. */
    public record FuelType(String itemId, int charges, long cooldownSeconds) {
    }

    /** Les 2 conteneurs adjacents utilises par l'auto-alimentation (peuvent etre le meme bloc s'il n'y en a qu'un seul),
     * avec la face de la machine sur laquelle chacun est colle (pour l'affichage sur l'hologramme). */
    public record AdjacentContainers(Block input, BlockFace inputFace, Block output, BlockFace outputFace) {
    }

    /**
     * Un palier structurel de machine (Bronze/Argent/Or...) : son propre materiau de bloc, sa
     * chance de reussite et son cooldown de base, sa jauge de carburant affichee, et l'id de
     * l'objet custom ("kit") qui permet d'y acceder DEPUIS le tier precedent. kitItemId est null
     * pour le tout premier tier (celui donne par /machine give, aucun kit necessaire).
     */
    public record MachineTier(String id, String displayName, Material block, double chance,
                               long cooldownSeconds, int fuelGaugeMax, String kitItemId) {
    }

    private final Plugin plugin;
    private final ConfigManager customItemsConfig;
    private final NamespacedKey machineKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey fuelKey;
    private final NamespacedKey lastUseKey;
    private final NamespacedKey activeCooldownKey;
    private final NamespacedKey bonusReussiteKey;
    private final NamespacedKey hologramKey;

    /** Machines actuellement posees dans le monde, pour l'effet de particules ambiant (perdu au redemarrage). */
    private final Set<Location> activeMachines = ConcurrentHashMap.newKeySet();

    /** LinkedHashMap : l'ordre de declaration dans machine-transformation.tiers fixe la progression
     * (le 1er tier declare est le tier de base, chaque tier suivant necessite le kit du precedent). */
    private final Map<String, MachineTier> tiers = new LinkedHashMap<>();
    private final Map<String, FuelType> fuelTypes = new LinkedHashMap<>();
    private String upgradeItemId = "amelioration_machine";
    private double bonusPerUpgrade = 5.0;
    private double bonusMax = 30.0;
    private boolean autoAlimentation = true;
    private boolean carburantSeulementSiEchec = false;
    private int hologramSegments = 10;
    private int hologramFuelGaugeMaxDefault = 20;
    private boolean hologramCompact = false;
    /** LinkedHashMap : l'ordre de declaration dans machine-transformation.minerais fixe la priorite
     * de traitement de l'auto-alimentation (le 1er minerai present dans le coffre d'entree est traite en premier). */
    private final Map<Material, String> acceptedOres = new LinkedHashMap<>();

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    public MachineManager(Plugin plugin, ConfigManager customItemsConfig) {
        this.plugin = plugin;
        this.customItemsConfig = customItemsConfig;
        this.machineKey = new NamespacedKey(plugin, "machine-transformation");
        this.tierKey = new NamespacedKey(plugin, "machine-tier");
        this.fuelKey = new NamespacedKey(plugin, "machine-carburant");
        this.lastUseKey = new NamespacedKey(plugin, "machine-derniere-utilisation");
        this.activeCooldownKey = new NamespacedKey(plugin, "machine-cooldown-actif");
        this.bonusReussiteKey = new NamespacedKey(plugin, "machine-bonus-reussite");
        this.hologramKey = new NamespacedKey(plugin, "machine-hologramme");
        loadConfig();
    }

    public void loadConfig() {
        acceptedOres.clear();
        fuelTypes.clear();
        tiers.clear();
        ConfigurationSection section = customItemsConfig.get().getConfigurationSection("machine-transformation");
        if (section == null) {
            plugin.getLogger().warning("Section 'machine-transformation' manquante dans custom_items.yml.");
            return;
        }

        hologramFuelGaugeMaxDefault = Math.max(1, section.getInt("hologramme-jauge-carburant-max", 20));

        ConfigurationSection tiersSection = section.getConfigurationSection("tiers");
        if (tiersSection != null) {
            for (String tierId : tiersSection.getKeys(false)) {
                ConfigurationSection tierSection = tiersSection.getConfigurationSection(tierId);
                if (tierSection == null) {
                    continue;
                }
                Material block = Material.matchMaterial(tierSection.getString("bloc", "IRON_BLOCK"));
                if (block == null) {
                    plugin.getLogger().warning("Materiau inconnu pour le tier de machine '" + tierId + "', IRON_BLOCK utilise.");
                    block = Material.IRON_BLOCK;
                }
                String displayName = tierSection.getString("nom", tierId);
                double chance = tierSection.getDouble("chance-reussite", 20.0);
                long cooldown = Math.max(0, tierSection.getLong("cooldown-secondes", 60L));
                int fuelGaugeMax = Math.max(1, tierSection.getInt("jauge-carburant-max", hologramFuelGaugeMaxDefault));
                String kitItemId = tierSection.contains("kit-item-id")
                        ? tierSection.getString("kit-item-id").toLowerCase() : null;
                tiers.put(tierId.toLowerCase(), new MachineTier(
                        tierId.toLowerCase(), displayName, block, chance, cooldown, fuelGaugeMax, kitItemId));
            }
        }
        if (tiers.isEmpty()) {
            plugin.getLogger().warning("Aucun tier configure dans machine-transformation.tiers : "
                    + "un tier 'bronze' par defaut (IRON_BLOCK, 20%, 60s) est utilise.");
            tiers.put("bronze", new MachineTier("bronze", "&7Bronze", Material.IRON_BLOCK, 20.0, 60L, 20, null));
        }

        ConfigurationSection carburants = section.getConfigurationSection("carburants");
        if (carburants != null) {
            for (String itemId : carburants.getKeys(false)) {
                ConfigurationSection fuelSection = carburants.getConfigurationSection(itemId);
                if (fuelSection == null) {
                    continue;
                }
                int charges = Math.max(1, fuelSection.getInt("charges", 5));
                long cooldown = Math.max(0, fuelSection.getLong("cooldown-secondes", getBaseTier().cooldownSeconds()));
                fuelTypes.put(itemId.toLowerCase(), new FuelType(itemId.toLowerCase(), charges, cooldown));
            }
        }
        if (fuelTypes.isEmpty()) {
            plugin.getLogger().warning("Aucun carburant configure dans machine-transformation.carburants.");
        }

        ConfigurationSection amelioration = section.getConfigurationSection("amelioration");
        if (amelioration != null) {
            upgradeItemId = amelioration.getString("item-id", "amelioration_machine").toLowerCase();
            bonusPerUpgrade = amelioration.getDouble("bonus-par-amelioration", 5.0);
            bonusMax = amelioration.getDouble("bonus-max", 30.0);
        }

        autoAlimentation = section.getBoolean("auto-alimentation", true);
        carburantSeulementSiEchec = section.getBoolean("carburant-uniquement-si-echec", false);
        hologramSegments = Math.max(1, section.getInt("hologramme-segments", 10));
        hologramCompact = section.getBoolean("hologramme-compact", false);

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
                + tiers.size() + " tier(s), " + fuelTypes.size() + " type(s) de carburant, "
                + "auto-alimentation " + (autoAlimentation ? "activee" : "desactivee") + ".");
    }

    // ---- Tiers ----

    /** Le tier de base (le 1er declare dans la config), celui donne par /machine give et /machine preset. */
    public MachineTier getBaseTier() {
        return tiers.values().stream().findFirst()
                .orElse(new MachineTier("bronze", "&7Bronze", Material.IRON_BLOCK, 20.0, 60L, 20, null));
    }

    public MachineTier getTier(String id) {
        return id == null ? null : tiers.get(id.toLowerCase());
    }

    /** Le tier suivant celui donne dans la progression, ou null si c'est deja le dernier tier. */
    public MachineTier getNextTier(String currentTierId) {
        boolean foundCurrent = false;
        for (MachineTier tier : tiers.values()) {
            if (foundCurrent) {
                return tier;
            }
            if (tier.id().equalsIgnoreCase(currentTierId)) {
                foundCurrent = true;
            }
        }
        return null;
    }

    /** Le tier accessible grace a cet id d'objet "kit", ou null si aucun tier n'utilise ce kit. */
    public MachineTier getTierForKitItem(String kitItemId) {
        if (kitItemId == null) {
            return null;
        }
        for (MachineTier tier : tiers.values()) {
            if (kitItemId.equalsIgnoreCase(tier.kitItemId())) {
                return tier;
            }
        }
        return null;
    }

    /** Le tier actuel de ce bloc (celui stocke en PDC), ou le tier de base si non defini/invalide. */
    public MachineTier getBlockTier(Block block) {
        String id = block.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        MachineTier tier = id != null ? getTier(id) : null;
        return tier != null ? tier : getBaseTier();
    }

    /**
     * Fait passer ce bloc au tier donne : change son materiau physique et sa chance/cooldown de
     * base, tout en preservant son etat (carburant, bonus, hologramme). Le cooldown actif est
     * reinitialise a celui du nouveau tier pour que l'amelioration soit immediatement ressentie.
     */
    public void setTier(Block block, MachineTier tier) {
        // Sauvegarde defensive avant de changer le materiau du bloc, au cas ou cela affecterait
        // le PersistentDataContainer associe a cette position.
        int fuel = getFuel(block);
        long lastUse = getLastUseMillis(block);
        double bonus = getBonusReussite(block);
        String hologramUuid = block.getPersistentDataContainer().get(hologramKey, PersistentDataType.STRING);

        block.setType(tier.block());

        block.getPersistentDataContainer().set(machineKey, PersistentDataType.BYTE, (byte) 1);
        block.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.id());
        setFuel(block, fuel);
        if (lastUse > 0) {
            block.getPersistentDataContainer().set(lastUseKey, PersistentDataType.LONG, lastUse);
        }
        setActiveCooldownSeconds(block, tier.cooldownSeconds());
        if (bonus > 0) {
            block.getPersistentDataContainer().set(bonusReussiteKey, PersistentDataType.DOUBLE, bonus);
        }
        if (hologramUuid != null) {
            block.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, hologramUuid);
        }
    }

    // ---- Carburant ----

    public boolean isFuelType(String itemId) {
        return itemId != null && fuelTypes.containsKey(itemId.toLowerCase());
    }

    public FuelType getFuelType(String itemId) {
        return itemId == null ? null : fuelTypes.get(itemId.toLowerCase());
    }

    /** Le carburant "de base" (le premier declare dans la config), utilise pour les messages generiques. */
    public FuelType getDefaultFuelType() {
        return fuelTypes.values().stream().findFirst().orElse(null);
    }

    // ---- Amelioration (bonus incremental, independant des tiers) ----

    public String getUpgradeItemId() {
        return upgradeItemId;
    }

    public double getBonusPerUpgrade() {
        return bonusPerUpgrade;
    }

    public double getBonusMax() {
        return bonusMax;
    }

    public boolean isAutoAlimentationEnabled() {
        return autoAlimentation;
    }

    /** Si true, le carburant n'est consomme que quand la transformation echoue (mode economique). */
    public boolean isConsumeFuelOnFailureOnly() {
        return carburantSeulementSiEchec;
    }

    public int getHologramSegments() {
        return hologramSegments;
    }

    public boolean isHologramCompact() {
        return hologramCompact;
    }

    /**
     * Conteneurs adjacents (coffre, coffre piege, baril ou hopper) utilises par l'auto-alimentation.
     * Avec un seul conteneur colle, il sert a la fois d'entree (minerais) et de sortie (Lucky Blocks).
     * Avec 2 conteneurs ou plus, le premier trouve (ordre nord/sud/est/ouest/haut/bas) sert d'entree
     * et le second de sortie, pour separer minerais et recompenses. Renvoie null si aucun n'est colle.
     */
    public AdjacentContainers getAdjacentContainers(Block machineBlock) {
        List<Block> foundBlocks = new ArrayList<>();
        List<BlockFace> foundFaces = new ArrayList<>();
        for (BlockFace face : ADJACENT_FACES) {
            Block relative = machineBlock.getRelative(face);
            Material type = relative.getType();
            if (type == Material.CHEST || type == Material.TRAPPED_CHEST
                    || type == Material.BARREL || type == Material.HOPPER) {
                foundBlocks.add(relative);
                foundFaces.add(face);
            }
        }
        if (foundBlocks.isEmpty()) {
            return null;
        }
        Block input = foundBlocks.get(0);
        BlockFace inputFace = foundFaces.get(0);
        boolean hasSeparateOutput = foundBlocks.size() > 1;
        Block output = hasSeparateOutput ? foundBlocks.get(1) : input;
        BlockFace outputFace = hasSeparateOutput ? foundFaces.get(1) : inputFace;
        return new AdjacentContainers(input, inputFace, output, outputFace);
    }

    /** Traduit une face en francais, pour l'afficher sur l'hologramme de la machine. */
    public static String faceLabel(BlockFace face) {
        return switch (face) {
            case NORTH -> "Nord";
            case SOUTH -> "Sud";
            case EAST -> "Est";
            case WEST -> "Ouest";
            case UP -> "Haut";
            case DOWN -> "Bas";
            default -> face.name();
        };
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
        MachineTier baseTier = getBaseTier();
        ItemStack item = new ItemStack(baseTier.block(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.color("&b&lMachine a Transformation"));
            meta.setLore(List.of(
                    MessageManager.color("&7Clic-droit avec un minerai en main"),
                    MessageManager.color("&7pour tenter de le transformer"),
                    MessageManager.color("&7en Lucky Block."),
                    MessageManager.color("&7Tier : " + baseTier.displayName()
                            + " &7(&e" + (int) baseTier.chance() + "%&7 de reussite)"),
                    MessageManager.color("&7Necessite du carburant pour fonctionner."),
                    MessageManager.color("&7Clic a vide pour voir son etat.")
            ));
            meta.getPersistentDataContainer().set(machineKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Marque un bloc pose comme etant la Machine a Transformation (au tier de base), l'enregistre
     * pour les particules ambiantes et fait apparaitre son hologramme d'etat. */
    public void tagBlock(Block block) {
        setTier(block, getBaseTier());
        activeMachines.add(block.getLocation());
        spawnHologram(block);
    }

    /** A appeler quand une machine est cassee, pour arreter ses particules ambiantes. */
    public void forgetMachine(Location location) {
        activeMachines.remove(location);
    }

    // ---- Hologramme d'etat (ArmorStand invisible affichant charges/cooldown/bonus) ----

    /** Fait apparaitre l'hologramme au-dessus du bloc, s'il n'en a pas deja un (ex: rechargement du plugin). */
    public void spawnHologram(Block block) {
        if (getHologram(block) != null) {
            return;
        }
        Location location = block.getLocation().add(0.5, 1.4, 0.5);
        ArmorStand stand = (ArmorStand) block.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName(MessageManager.color("&b&lMachine a Transformation"));
        stand.setPersistent(true);
        block.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, stand.getUniqueId().toString());
    }

    /** Hologramme associe a cette machine, ou null s'il n'existe pas (jamais cree ou deja retire). */
    public ArmorStand getHologram(Block block) {
        String raw = block.getPersistentDataContainer().get(hologramKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            Entity entity = Bukkit.getEntity(UUID.fromString(raw));
            return entity instanceof ArmorStand stand ? stand : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Retire l'hologramme de cette machine (a appeler quand le bloc est casse). */
    public void removeHologram(Block block) {
        ArmorStand stand = getHologram(block);
        if (stand != null) {
            stand.remove();
        }
        block.getPersistentDataContainer().remove(hologramKey);
    }

    /** Emplacements de toutes les machines connues depuis le demarrage du plugin (effet de particules ambiant). */
    public Set<Location> getActiveMachineLocations() {
        return activeMachines;
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

    // ---- Etat de la machine (carburant, cooldown, bonus), stocke sur le bloc ----

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

    /** Cooldown actuellement applique par cette machine (herite du dernier carburant utilise, ou
     * du tier actuel si jamais ravitaillee depuis sa pose/son dernier changement de tier). */
    public long getActiveCooldownSeconds(Block block) {
        Long value = block.getPersistentDataContainer().get(activeCooldownKey, PersistentDataType.LONG);
        return value != null ? value : getBlockTier(block).cooldownSeconds();
    }

    public void setActiveCooldownSeconds(Block block, long seconds) {
        block.getPersistentDataContainer().set(activeCooldownKey, PersistentDataType.LONG, Math.max(0, seconds));
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
        return (lastUse + getActiveCooldownSeconds(block) * 1000L) - System.currentTimeMillis();
    }

    /** Bonus de reussite accumule sur cette machine grace aux ameliorations (0 par defaut). */
    public double getBonusReussite(Block block) {
        Double value = block.getPersistentDataContainer().get(bonusReussiteKey, PersistentDataType.DOUBLE);
        return value == null ? 0.0 : value;
    }

    /** Ajoute du bonus de reussite (plafonne a bonus-max). Renvoie le nouveau total. */
    public double addBonusReussite(Block block, double amount) {
        double newTotal = Math.min(bonusMax, getBonusReussite(block) + amount);
        block.getPersistentDataContainer().set(bonusReussiteKey, PersistentDataType.DOUBLE, newTotal);
        return newTotal;
    }

    /** Chance de reussite effective de cette machine (chance du tier + bonus d'amelioration), plafonnee a 100%. */
    public double getEffectiveChance(Block block) {
        return Math.min(100.0, getBlockTier(block).chance() + getBonusReussite(block));
    }
}
