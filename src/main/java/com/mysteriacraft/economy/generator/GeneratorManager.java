package com.mysteriacraft.economy.generator;

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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charge la configuration des Generateurs d'Argent (generateurs.yml) et fabrique/marque leurs
 * blocs. Chaque generateur pose accumule de l'argent en continu (base sur le temps ecoule reel,
 * meme hors-ligne) jusqu'a un plafond de stockage, stocke directement sur le bloc via
 * PersistentDataContainer (extension Paper). Clic-droit dessus (voir GeneratorService) recupere
 * tout l'argent stocke sur le solde du joueur.
 */
public class GeneratorManager {

    /** Un type de generateur : bloc, argent genere par cycle complet, duree du cycle, plafond de stockage. */
    public record GeneratorType(String id, String displayName, Material block, double amount,
                                 long intervalSeconds, double storageMax, boolean rewardOnly) {
        /** Argent genere par seconde reelle (avant bonus d'amelioration). */
        public double ratePerSecond() {
            return intervalSeconds > 0 ? amount / intervalSeconds : 0.0;
        }
    }

    private final Plugin plugin;
    private final ConfigManager generatorsConfig;
    private final NamespacedKey generatorKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey storedKey;
    private final NamespacedKey lastTickKey;
    private final NamespacedKey hologramKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey bonusKey;

    private final Map<String, GeneratorType> types = new LinkedHashMap<>();
    private long tickSeconds = 5;
    private int maxPerPlayer = 30;
    private double taxPercent = 0;
    private String upgradeItemId = "boost_generateur";
    private double bonusPerUpgradePercent = 10;
    private double bonusMaxPercent = 50;

    /** Generateurs actuellement poses dans le monde, pour l'accumulation/l'hologramme (perdu au redemarrage,
     * mais l'argent deja stocke sur chaque bloc ne l'est pas : il est recalcule des la prochaine interaction/tick). */
    private final Set<Location> activeGenerators = ConcurrentHashMap.newKeySet();

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0");
    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    public GeneratorManager(Plugin plugin, ConfigManager generatorsConfig) {
        this.plugin = plugin;
        this.generatorsConfig = generatorsConfig;
        this.generatorKey = new NamespacedKey(plugin, "generateur");
        this.typeKey = new NamespacedKey(plugin, "generateur-type");
        this.storedKey = new NamespacedKey(plugin, "generateur-stock");
        this.lastTickKey = new NamespacedKey(plugin, "generateur-dernier-tick");
        this.hologramKey = new NamespacedKey(plugin, "generateur-hologramme");
        this.ownerKey = new NamespacedKey(plugin, "generateur-proprietaire");
        this.bonusKey = new NamespacedKey(plugin, "generateur-bonus-rythme");
        loadConfig();
    }

    public void loadConfig() {
        types.clear();
        ConfigurationSection root = generatorsConfig.get().getConfigurationSection("generateurs");
        if (root == null) {
            plugin.getLogger().warning("Section 'generateurs' manquante dans generateurs.yml.");
        } else {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                Material block = Material.matchMaterial(section.getString("bloc", "IRON_ORE"));
                if (block == null) {
                    plugin.getLogger().warning("Materiau inconnu pour le generateur '" + id + "', IRON_ORE utilise.");
                    block = Material.IRON_ORE;
                }
                String displayName = section.getString("nom", id);
                double amount = Math.max(0, section.getDouble("montant", 0));
                long intervalSeconds = Math.max(1, section.getLong("intervalle-secondes", 3600));
                double storageMax = Math.max(0, section.getDouble("stockage-max", amount));
                boolean rewardOnly = section.getBoolean("obtenable-en-recompense-uniquement", false);
                types.put(id.toLowerCase(), new GeneratorType(
                        id.toLowerCase(), displayName, block, amount, intervalSeconds, storageMax, rewardOnly));
            }
        }

        tickSeconds = Math.max(1, generatorsConfig.get().getLong("tick-secondes", 5));
        maxPerPlayer = Math.max(0, generatorsConfig.get().getInt("max-generateurs-par-joueur", 30));
        taxPercent = Math.min(100, Math.max(0, generatorsConfig.get().getDouble("taxe-recuperation-pourcent", 0)));

        ConfigurationSection amelioration = generatorsConfig.get().getConfigurationSection("amelioration");
        if (amelioration != null) {
            upgradeItemId = amelioration.getString("item-id", "boost_generateur").toLowerCase();
            bonusPerUpgradePercent = amelioration.getDouble("bonus-par-amelioration", 10.0);
            bonusMaxPercent = amelioration.getDouble("bonus-max", 50.0);
        }

        plugin.getLogger().info("Generateurs d'argent : " + types.size() + " type(s) charge(s), tick toutes les "
                + tickSeconds + "s, max " + maxPerPlayer + " par joueur, taxe " + taxPercent + "%.");
    }

    public GeneratorType getType(String id) {
        return id == null ? null : types.get(id.toLowerCase());
    }

    public List<GeneratorType> getTypes() {
        return List.copyOf(types.values());
    }

    public long getTickSeconds() {
        return tickSeconds;
    }

    public int getMaxPerPlayer() {
        return maxPerPlayer;
    }

    public double getTaxPercent() {
        return taxPercent;
    }

    // ---- Amelioration de rythme (bonus incremental par generateur) ----

    public String getUpgradeItemId() {
        return upgradeItemId;
    }

    public double getBonusPerUpgradePercent() {
        return bonusPerUpgradePercent;
    }

    public double getBonusMaxPercent() {
        return bonusMaxPercent;
    }

    public double getBonusPercent(Block block) {
        Double value = block.getPersistentDataContainer().get(bonusKey, PersistentDataType.DOUBLE);
        return value == null ? 0.0 : value;
    }

    /** Ajoute du bonus de rythme (plafonne a bonus-max). Renvoie le nouveau total. */
    public double addBonusPercent(Block block, double amount) {
        double newTotal = Math.min(bonusMaxPercent, getBonusPercent(block) + amount);
        block.getPersistentDataContainer().set(bonusKey, PersistentDataType.DOUBLE, newTotal);
        return newTotal;
    }

    /** Argent genere par seconde reelle pour CE bloc, bonus d'amelioration inclus. */
    public double getEffectiveRatePerSecond(Block block) {
        GeneratorType type = getBlockType(block);
        if (type == null) {
            return 0.0;
        }
        return type.ratePerSecond() * (1.0 + getBonusPercent(block) / 100.0);
    }

    public ItemStack createGeneratorItem(GeneratorType type) {
        return createGeneratorItem(type, 1);
    }

    public ItemStack createGeneratorItem(GeneratorType type, int amount) {
        ItemStack item = new ItemStack(type.block(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.color(type.displayName()));
            double perHour = type.ratePerSecond() * 3600;
            List<String> lore = new ArrayList<>(List.of(
                    MessageManager.color("&7Genere de l'argent automatiquement,"),
                    MessageManager.color("&7meme hors-ligne."),
                    MessageManager.color("&7Rythme : &e~" + NUMBER_FORMAT.format(perHour) + "&7/heure"),
                    MessageManager.color("&7Stockage max : &e" + NUMBER_FORMAT.format(type.storageMax())),
                    MessageManager.color("&7Clic-droit pour recuperer l'argent stocke.")
            ));
            if (type.rewardOnly()) {
                lore.add(MessageManager.color("&5Uniquement obtenable en recompense"));
                lore.add(MessageManager.color("&5(crate, battlepass, quete, luckyblock)."));
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(generatorKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isGeneratorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(generatorKey, PersistentDataType.BYTE);
    }

    /** Type de generateur marque sur cet ItemStack, ou null si ce n'en est pas un (ou type inconnu). */
    public GeneratorType getItemGeneratorType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return getType(id);
    }

    /** Marque un bloc pose comme etant ce type de generateur pour ce proprietaire, initialise son
     * stock a 0 et fait apparaitre son hologramme d'etat. */
    public void tagBlock(Block block, GeneratorType type, UUID owner) {
        block.setType(type.block());
        block.getPersistentDataContainer().set(generatorKey, PersistentDataType.BYTE, (byte) 1);
        block.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
        block.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        setStored(block, 0.0);
        setLastTickMillis(block, System.currentTimeMillis());
        activeGenerators.add(block.getLocation());
        spawnHologram(block);
    }

    /** A appeler quand un generateur est casse, pour arreter son suivi (accumulation/hologramme). */
    public void forgetGenerator(Location location) {
        activeGenerators.remove(location);
    }

    public Set<Location> getActiveGeneratorLocations() {
        return activeGenerators;
    }

    public boolean isGeneratorBlock(Block block) {
        return block.getPersistentDataContainer().has(generatorKey, PersistentDataType.BYTE);
    }

    /** Type de generateur de ce bloc, ou null si non marque ou si son type a disparu de la config. */
    public GeneratorType getBlockType(Block block) {
        String id = block.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return getType(id);
    }

    /** UUID du proprietaire de ce generateur (celui qui l'a pose), ou null si inconnu (bloc pose
     * avant l'ajout de cette fonctionnalite). */
    public UUID getOwner(Block block) {
        String raw = block.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Nombre de generateurs actuellement suivis (session en cours) appartenant a ce joueur.
     * Limitation connue : ne compte que les generateurs vus depuis le dernier demarrage du plugin
     * (comme les particules ambiantes de la Machine a Transformation), pas ceux jamais revisites. */
    public int countOwnedGenerators(UUID owner) {
        int count = 0;
        for (Location location : activeGenerators) {
            Block block = location.getBlock();
            if (isGeneratorBlock(block) && owner.equals(getOwner(block))) {
                count++;
            }
        }
        return count;
    }

    /** Bloc conteneur (hopper) colle a une face de ce generateur, ou null. Coller un hopper active
     * l'auto-collecte : l'argent genere est credite en continu au proprietaire sans clic-droit. */
    public Block getAdjacentHopper(Block generatorBlock) {
        for (BlockFace face : ADJACENT_FACES) {
            Block relative = generatorBlock.getRelative(face);
            if (relative.getType() == Material.HOPPER) {
                return relative;
            }
        }
        return null;
    }

    // ---- Stock d'argent (accumule en continu), stocke sur le bloc ----

    public double getStored(Block block) {
        Double value = block.getPersistentDataContainer().get(storedKey, PersistentDataType.DOUBLE);
        return value == null ? 0.0 : value;
    }

    public void setStored(Block block, double amount) {
        block.getPersistentDataContainer().set(storedKey, PersistentDataType.DOUBLE, Math.max(0, amount));
    }

    public long getLastTickMillis(Block block) {
        Long value = block.getPersistentDataContainer().get(lastTickKey, PersistentDataType.LONG);
        return value == null ? System.currentTimeMillis() : value;
    }

    public void setLastTickMillis(Block block, long millis) {
        block.getPersistentDataContainer().set(lastTickKey, PersistentDataType.LONG, millis);
    }

    /** Recalcule le stock en fonction du temps ecoule reel depuis le dernier tick (rythme effectif,
     * bonus d'amelioration inclus), plafonne a storage-max, et avance l'horodatage. Renvoie le
     * nouveau stock. Aucun effet si le type est inconnu. */
    public double accrue(Block block) {
        GeneratorType type = getBlockType(block);
        if (type == null) {
            return getStored(block);
        }
        long now = System.currentTimeMillis();
        long lastTick = getLastTickMillis(block);
        double elapsedSeconds = Math.max(0, (now - lastTick) / 1000.0);

        double newStored = Math.min(type.storageMax(), getStored(block) + elapsedSeconds * getEffectiveRatePerSecond(block));
        setStored(block, newStored);
        setLastTickMillis(block, now);
        return newStored;
    }

    /** Recalcule le stock (accrue) puis le vide integralement. Renvoie le montant recupere (0 si rien). */
    public double collect(Block block) {
        double stored = accrue(block);
        if (stored <= 0) {
            return 0.0;
        }
        setStored(block, 0.0);
        return stored;
    }

    // ---- Hologramme d'etat (ArmorStand invisible affichant le stock accumule) ----

    public void spawnHologram(Block block) {
        if (getHologram(block) != null) {
            return;
        }
        Location location = block.getLocation().add(0.5, 1.3, 0.5);
        ArmorStand stand = (ArmorStand) block.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName(MessageManager.color("&2&lGenerateur"));
        stand.setPersistent(true);
        block.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, stand.getUniqueId().toString());
    }

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

    public void removeHologram(Block block) {
        ArmorStand stand = getHologram(block);
        if (stand != null) {
            stand.remove();
        }
        block.getPersistentDataContainer().remove(hologramKey);
    }
}
