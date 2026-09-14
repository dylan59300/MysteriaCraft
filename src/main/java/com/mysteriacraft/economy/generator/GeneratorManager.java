package com.mysteriacraft.economy.generator;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.text.DecimalFormat;
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
                                 long intervalSeconds, double storageMax) {
        /** Argent genere par seconde reelle. */
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

    private final Map<String, GeneratorType> types = new LinkedHashMap<>();
    private long tickSeconds = 5;

    /** Generateurs actuellement poses dans le monde, pour l'accumulation/l'hologramme (perdu au redemarrage,
     * mais l'argent deja stocke sur chaque bloc ne l'est pas : il est recalcule des la prochaine interaction/tick). */
    private final Set<Location> activeGenerators = ConcurrentHashMap.newKeySet();

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0");

    public GeneratorManager(Plugin plugin, ConfigManager generatorsConfig) {
        this.plugin = plugin;
        this.generatorsConfig = generatorsConfig;
        this.generatorKey = new NamespacedKey(plugin, "generateur");
        this.typeKey = new NamespacedKey(plugin, "generateur-type");
        this.storedKey = new NamespacedKey(plugin, "generateur-stock");
        this.lastTickKey = new NamespacedKey(plugin, "generateur-dernier-tick");
        this.hologramKey = new NamespacedKey(plugin, "generateur-hologramme");
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
                types.put(id.toLowerCase(), new GeneratorType(
                        id.toLowerCase(), displayName, block, amount, intervalSeconds, storageMax));
            }
        }

        tickSeconds = Math.max(1, generatorsConfig.get().getLong("tick-secondes", 5));
        plugin.getLogger().info("Generateurs d'argent : " + types.size() + " type(s) charge(s), tick toutes les "
                + tickSeconds + "s.");
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

    public ItemStack createGeneratorItem(GeneratorType type) {
        return createGeneratorItem(type, 1);
    }

    public ItemStack createGeneratorItem(GeneratorType type, int amount) {
        ItemStack item = new ItemStack(type.block(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.color(type.displayName()));
            double perHour = type.ratePerSecond() * 3600;
            meta.setLore(List.of(
                    MessageManager.color("&7Genere de l'argent automatiquement,"),
                    MessageManager.color("&7meme hors-ligne."),
                    MessageManager.color("&7Rythme : &e~" + NUMBER_FORMAT.format(perHour) + "&7/heure"),
                    MessageManager.color("&7Stockage max : &e" + NUMBER_FORMAT.format(type.storageMax())),
                    MessageManager.color("&7Clic-droit pour recuperer l'argent stocke.")
            ));
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

    /** Marque un bloc pose comme etant ce type de generateur, initialise son stock a 0 et fait
     * apparaitre son hologramme d'etat. */
    public void tagBlock(Block block, GeneratorType type) {
        block.setType(type.block());
        block.getPersistentDataContainer().set(generatorKey, PersistentDataType.BYTE, (byte) 1);
        block.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
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

    /** Recalcule le stock en fonction du temps ecoule reel depuis le dernier tick, plafonne a
     * storage-max, et avance l'horodatage. Renvoie le nouveau stock. Aucun effet si le type est inconnu. */
    public double accrue(Block block) {
        GeneratorType type = getBlockType(block);
        if (type == null) {
            return getStored(block);
        }
        long now = System.currentTimeMillis();
        long lastTick = getLastTickMillis(block);
        double elapsedSeconds = Math.max(0, (now - lastTick) / 1000.0);

        double newStored = Math.min(type.storageMax(), getStored(block) + elapsedSeconds * type.ratePerSecond());
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
