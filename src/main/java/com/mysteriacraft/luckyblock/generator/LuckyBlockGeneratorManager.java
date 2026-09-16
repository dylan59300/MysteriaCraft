package com.mysteriacraft.luckyblock.generator;

import com.mysteriacraft.core.RecipeIngredient;
import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.storage.Database;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charge la configuration du Generateur de Lucky Block (luckyblock_generateur.yml) et suit l'etat
 * de chaque generateur pose : proprietaire, famille de Lucky Block ciblee (choisie a la creation
 * de l'item, voir createItem/getFamilyIdFromItem), carburant restant (en Lucky Blocks producibles)
 * et son hologramme. Comme les autres blocs "actifs" du plugin (Machine a Miner, Machine a
 * Transformation, Generateurs d'argent/ressources), un Block "nu" n'a pas de
 * PersistentDataContainer sur paper-api 1.20.1 : l'etat est suivi via une table SQLite dediee
 * indexee par position, mise en cache memoire (write-through).
 */
public class LuckyBlockGeneratorManager {

    /** Un type de carburant utilisable : nombre de Lucky Blocks qu'il permet de produire. */
    public record FuelType(String itemId, int production) {
    }

    private static final class GeneratorState {
        UUID owner;
        String familyId;
        int fuel;
        long lastTickMillis;
        UUID hologramUuid;
        double bonusRythme;
        double bonusStockage;
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager configManager;
    private final LuckyBlockManager luckyBlockManager;
    private final NamespacedKey generatorKey;
    private final NamespacedKey familyKey;
    private final NamespacedKey recipeKey;

    private final Map<Location, GeneratorState> generators = new ConcurrentHashMap<>();
    private final Map<String, FuelType> fuelTypes = new LinkedHashMap<>();

    private Material blockMaterial = Material.BEACON;
    private long intervalSeconds = 300L;
    private String craftFamilyId = "commune";
    private int maxPerPlayer = 3;
    private List<RecipeIngredient> recipe = new ArrayList<>();

    private int fuelMax = 500;
    private String rateBonusItemId = "boost_generateur_lb";
    private double rateBonusPerUpgrade = 10.0;
    private double rateBonusMax = 50.0;
    private String storageBonusItemId = "boost_stockage_generateur_lb";
    private double storageBonusPerUpgrade = 20.0;
    private double storageBonusMax = 100.0;

    public LuckyBlockGeneratorManager(Plugin plugin, Database database, ConfigManager configManager,
                                       LuckyBlockManager luckyBlockManager) {
        this.plugin = plugin;
        this.database = database;
        this.configManager = configManager;
        this.luckyBlockManager = luckyBlockManager;
        this.generatorKey = new NamespacedKey(plugin, "generateur-luckyblock");
        this.familyKey = new NamespacedKey(plugin, "generateur-luckyblock-famille");
        this.recipeKey = new NamespacedKey(plugin, "generateur-luckyblock-recette");
        createTable();
        loadGenerators();
        loadConfig();
        registerRecipe();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS generateurs_luckyblock (" +
                "monde TEXT NOT NULL, " +
                "x INTEGER NOT NULL, " +
                "y INTEGER NOT NULL, " +
                "z INTEGER NOT NULL, " +
                "proprietaire TEXT, " +
                "famille_id TEXT NOT NULL, " +
                "carburant INTEGER NOT NULL DEFAULT 0, " +
                "derniere_maj INTEGER NOT NULL DEFAULT 0, " +
                "hologramme_uuid TEXT, " +
                "bonus_rythme REAL NOT NULL DEFAULT 0, " +
                "bonus_stockage REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (monde, x, y, z)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'generateurs_luckyblock' : " + e.getMessage());
        }
        // Migration : une base creee AVANT l'ajout des bonus n'a pas ces colonnes.
        try (PreparedStatement s1 = connection.prepareStatement(
                "ALTER TABLE generateurs_luckyblock ADD COLUMN bonus_rythme REAL NOT NULL DEFAULT 0;")) {
            s1.executeUpdate();
        } catch (SQLException ignored) {
            // Colonne deja presente : rien a faire.
        }
        try (PreparedStatement s2 = connection.prepareStatement(
                "ALTER TABLE generateurs_luckyblock ADD COLUMN bonus_stockage REAL NOT NULL DEFAULT 0;")) {
            s2.executeUpdate();
        } catch (SQLException ignored) {
            // Colonne deja presente : rien a faire.
        }
    }

    private void loadGenerators() {
        generators.clear();
        String select = "SELECT monde, x, y, z, proprietaire, famille_id, carburant, derniere_maj, "
                + "hologramme_uuid, bonus_rythme, bonus_stockage FROM generateurs_luckyblock;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                World world = Bukkit.getWorld(rs.getString("monde"));
                if (world == null) {
                    continue;
                }
                Location location = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                GeneratorState state = new GeneratorState();
                String ownerRaw = rs.getString("proprietaire");
                if (ownerRaw != null) {
                    try {
                        state.owner = UUID.fromString(ownerRaw);
                    } catch (IllegalArgumentException ignored) {
                        // UUID corrompu : le generateur reste sans proprietaire connu.
                    }
                }
                state.familyId = rs.getString("famille_id");
                state.fuel = rs.getInt("carburant");
                state.lastTickMillis = rs.getLong("derniere_maj");
                String hologramRaw = rs.getString("hologramme_uuid");
                if (hologramRaw != null) {
                    try {
                        state.hologramUuid = UUID.fromString(hologramRaw);
                    } catch (IllegalArgumentException ignored) {
                        // UUID corrompu : l'hologramme sera simplement recree au besoin.
                    }
                }
                state.bonusRythme = rs.getDouble("bonus_rythme");
                state.bonusStockage = rs.getDouble("bonus_stockage");
                generators.put(location, state);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur chargement des Generateurs de Lucky Block : " + e.getMessage());
        }
        plugin.getLogger().info(generators.size() + " Generateur(s) de Lucky Block recharge(s) depuis la base.");
    }

    private void persistAsync(Location location, GeneratorState state) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String upsert = "INSERT INTO generateurs_luckyblock (monde, x, y, z, proprietaire, famille_id, "
                    + "carburant, derniere_maj, hologramme_uuid, bonus_rythme, bonus_stockage) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(monde, x, y, z) DO UPDATE SET proprietaire = excluded.proprietaire, "
                    + "famille_id = excluded.famille_id, carburant = excluded.carburant, "
                    + "derniere_maj = excluded.derniere_maj, hologramme_uuid = excluded.hologramme_uuid, "
                    + "bonus_rythme = excluded.bonus_rythme, bonus_stockage = excluded.bonus_stockage;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, location.getWorld().getName());
                statement.setInt(2, location.getBlockX());
                statement.setInt(3, location.getBlockY());
                statement.setInt(4, location.getBlockZ());
                statement.setString(5, state.owner != null ? state.owner.toString() : null);
                statement.setString(6, state.familyId);
                statement.setInt(7, state.fuel);
                statement.setLong(8, state.lastTickMillis);
                statement.setString(9, state.hologramUuid != null ? state.hologramUuid.toString() : null);
                statement.setDouble(10, state.bonusRythme);
                statement.setDouble(11, state.bonusStockage);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur sauvegarde generateur de Lucky Block : " + e.getMessage());
            }
        });
    }

    private void deleteAsync(Location location) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String delete = "DELETE FROM generateurs_luckyblock WHERE monde = ? AND x = ? AND y = ? AND z = ?;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                statement.setString(1, location.getWorld().getName());
                statement.setInt(2, location.getBlockX());
                statement.setInt(3, location.getBlockY());
                statement.setInt(4, location.getBlockZ());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur suppression generateur de Lucky Block : " + e.getMessage());
            }
        });
    }

    private static Location blockKey(Block block) {
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    public void loadConfig() {
        fuelTypes.clear();
        recipe = new ArrayList<>();

        ConfigurationSection section = configManager.get().getConfigurationSection("generateur-luckyblock");
        if (section == null) {
            plugin.getLogger().warning("Section 'generateur-luckyblock' manquante dans luckyblock_generateur.yml.");
            return;
        }

        Material configuredBlock = Material.matchMaterial(section.getString("bloc", "BEACON"));
        blockMaterial = configuredBlock != null ? configuredBlock : Material.BEACON;
        intervalSeconds = Math.max(1, section.getLong("intervalle-secondes", 300L));
        craftFamilyId = section.getString("famille-craft", "commune").toLowerCase();
        maxPerPlayer = Math.max(0, section.getInt("max-generateurs-par-joueur", 3));

        ConfigurationSection carburants = section.getConfigurationSection("carburants");
        if (carburants != null) {
            for (String itemId : carburants.getKeys(false)) {
                ConfigurationSection fuelSection = carburants.getConfigurationSection(itemId);
                if (fuelSection == null) {
                    continue;
                }
                int production = Math.max(1, fuelSection.getInt("production", 1));
                fuelTypes.put(itemId.toLowerCase(), new FuelType(itemId.toLowerCase(), production));
            }
        }
        if (fuelTypes.isEmpty()) {
            plugin.getLogger().warning("Aucun carburant configure dans generateur-luckyblock.carburants.");
        }

        for (Map<?, ?> raw : section.getMapList("recette")) {
            Material ingredientMaterial = Material.matchMaterial(String.valueOf(raw.get("materiel")));
            if (ingredientMaterial == null) {
                continue;
            }
            int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
            recipe.add(new RecipeIngredient(ingredientMaterial, Math.max(1, amount)));
        }

        fuelMax = Math.max(1, section.getInt("carburant-max", 500));

        ConfigurationSection amelioration = section.getConfigurationSection("amelioration");
        if (amelioration != null) {
            rateBonusItemId = amelioration.getString("item-id", "boost_generateur_lb").toLowerCase();
            rateBonusPerUpgrade = Math.max(0, amelioration.getDouble("bonus-par-amelioration", 10.0));
            rateBonusMax = Math.max(0, amelioration.getDouble("bonus-max", 50.0));
        }

        ConfigurationSection ameliorationStockage = section.getConfigurationSection("amelioration-stockage");
        if (ameliorationStockage != null) {
            storageBonusItemId = ameliorationStockage.getString("item-id", "boost_stockage_generateur_lb").toLowerCase();
            storageBonusPerUpgrade = Math.max(0, ameliorationStockage.getDouble("bonus-par-amelioration", 20.0));
            storageBonusMax = Math.max(0, ameliorationStockage.getDouble("bonus-max", 100.0));
        }

        plugin.getLogger().info("Generateur de Lucky Block : " + fuelTypes.size() + " type(s) de carburant, "
                + "intervalle " + intervalSeconds + "s, famille de craft '" + craftFamilyId + "'.");
    }

    public long getIntervalSeconds() {
        return intervalSeconds;
    }

    public int getMaxPerPlayer() {
        return maxPerPlayer;
    }

    public FuelType getFuelType(String itemId) {
        return itemId == null ? null : fuelTypes.get(itemId.toLowerCase());
    }

    // ---- Amelioration de rythme (voir generateur-luckyblock.amelioration) ----

    public String getRateBonusItemId() {
        return rateBonusItemId;
    }

    public double getRateBonusMax() {
        return rateBonusMax;
    }

    public double getBonusRythme(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? 0 : state.bonusRythme;
    }

    /** Ajoute le bonus de rythme (plafonne a bonus-max). Renvoie le nouveau total. */
    public double addBonusRythme(Block block, double amount) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null) {
            return 0;
        }
        state.bonusRythme = Math.min(rateBonusMax, state.bonusRythme + amount);
        persistAsync(key, state);
        return state.bonusRythme;
    }

    /** Intervalle EFFECTIF entre 2 productions (rythme de base reduit par le bonus de rythme
     * accumule), jamais en dessous d'1 seconde. */
    public long getEffectiveIntervalSeconds(Block block) {
        double bonus = getBonusRythme(block);
        return Math.max(1, Math.round(intervalSeconds * (1 - bonus / 100.0)));
    }

    // ---- Amelioration de stockage de carburant (voir generateur-luckyblock.amelioration-stockage) ----

    public String getStorageBonusItemId() {
        return storageBonusItemId;
    }

    public double getStorageBonusMax() {
        return storageBonusMax;
    }

    public double getBonusStockage(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? 0 : state.bonusStockage;
    }

    /** Ajoute le bonus de stockage de carburant (plafonne a bonus-max). Renvoie le nouveau total. */
    public double addBonusStockage(Block block, double amount) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null) {
            return 0;
        }
        state.bonusStockage = Math.min(storageBonusMax, state.bonusStockage + amount);
        persistAsync(key, state);
        return state.bonusStockage;
    }

    /** Plafond EFFECTIF de carburant stockable (plafond de base augmente par le bonus de stockage
     * accumule). */
    public int getEffectiveFuelMax(Block block) {
        double bonus = getBonusStockage(block);
        return (int) Math.round(fuelMax * (1 + bonus / 100.0));
    }

    // ---- Item / marquage du bloc ----

    /** Cree l'item posable pour une famille donnee (voir /generateurlb give). */
    public ItemStack createItem(LuckyBlockFamily family, int amount) {
        ItemStack item = new ItemStack(blockMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.color("&d&lGenerateur de Lucky Block &7[" + family.displayName() + "&7]"));
            meta.setLore(List.of(
                    MessageManager.color("&7Produit automatiquement un"),
                    MessageManager.color("&7" + family.displayName() + " &7toutes les"),
                    MessageManager.color("&7" + intervalSeconds + " secondes, tant qu'il"),
                    MessageManager.color("&7lui reste du carburant."),
                    MessageManager.color("&7Clic-droit avec du carburant pour l'alimenter."),
                    MessageManager.color("&7Colle un coffre/baril/hopper pour recuperer"),
                    MessageManager.color("&7automatiquement les Lucky Blocks produits.")
            ));
            meta.getPersistentDataContainer().set(generatorKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(familyKey, PersistentDataType.STRING, family.id());
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

    /** Id de famille stocke sur cet item (voir createItem), ou null si ce n'est pas un item de
     * Generateur de Lucky Block valide. */
    public String getFamilyIdFromItem(ItemStack item) {
        if (!isGeneratorItem(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(familyKey, PersistentDataType.STRING);
    }

    /** Marque un bloc pose comme etant un Generateur de Lucky Block appartenant a ce joueur,
     * ciblant cette famille, et fait apparaitre son hologramme d'etat. */
    public void tagBlock(Block block, UUID owner, String familyId) {
        Location key = blockKey(block);
        GeneratorState state = new GeneratorState();
        state.owner = owner;
        state.familyId = familyId;
        state.lastTickMillis = System.currentTimeMillis();
        generators.put(key, state);
        persistAsync(key, state);
        spawnHologram(block);
    }

    public void forgetGenerator(Location location) {
        generators.remove(location);
        deleteAsync(location);
    }

    public boolean isGeneratorBlock(Block block) {
        return generators.containsKey(blockKey(block));
    }

    public Set<Location> getActiveGeneratorLocations() {
        return generators.keySet();
    }

    public UUID getOwner(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? null : state.owner;
    }

    public String getFamilyId(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? null : state.familyId;
    }

    public LuckyBlockFamily getFamily(Block block) {
        String familyId = getFamilyId(block);
        return familyId == null ? null : luckyBlockManager.getFamily(familyId);
    }

    /** Nombre de Generateurs de Lucky Block actuellement suivis appartenant a ce joueur (charges
     * au demarrage + poses depuis). */
    public int countOwnedGenerators(UUID owner) {
        int count = 0;
        for (GeneratorState state : generators.values()) {
            if (owner.equals(state.owner)) {
                count++;
            }
        }
        return count;
    }

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    /** Premier coffre/coffre piege/baril/hopper colle au generateur (sortie des Lucky Blocks), ou
     * null si aucun n'est colle (les Lucky Blocks produits sont alors deposes au sol a son pied). */
    public Block getOutputContainer(Block generatorBlock) {
        for (BlockFace face : ADJACENT_FACES) {
            Block relative = generatorBlock.getRelative(face);
            Material type = relative.getType();
            if (type == Material.CHEST || type == Material.TRAPPED_CHEST
                    || type == Material.BARREL || type == Material.HOPPER) {
                return relative;
            }
        }
        return null;
    }

    // ---- Hologramme d'etat (ArmorStand invisible affichant famille/carburant) ----

    public void spawnHologram(Block block) {
        if (getHologram(block) != null) {
            return;
        }
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null) {
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
        stand.setCustomName(MessageManager.color("&d&lGenerateur de Lucky Block"));
        stand.setPersistent(true);
        state.hologramUuid = stand.getUniqueId();
        persistAsync(key, state);
    }

    public ArmorStand getHologram(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        if (state == null || state.hologramUuid == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(state.hologramUuid);
        return entity instanceof ArmorStand stand ? stand : null;
    }

    public void removeHologram(Block block) {
        ArmorStand stand = getHologram(block);
        if (stand != null) {
            stand.remove();
        }
        GeneratorState state = generators.get(blockKey(block));
        if (state != null) {
            state.hologramUuid = null;
        }
    }

    // ---- Etat (carburant) ----

    public int getFuel(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? 0 : state.fuel;
    }

    /** Ajoute du carburant (en Lucky Blocks producibles), plafonne a getEffectiveFuelMax(block) (le
     * surplus est perdu). Redemarre le compte a rebours de production si le generateur etait a
     * court de carburant, pour ne pas produire instantanement tout le temps ecoule pendant qu'il
     * etait vide. Renvoie la quantite REELLEMENT ajoutee (peut etre inferieure a "amount" si le
     * plafond a ete atteint). */
    public int refuel(Block block, int amount) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null) {
            return 0;
        }
        if (state.fuel <= 0) {
            state.lastTickMillis = System.currentTimeMillis();
        }
        int max = getEffectiveFuelMax(block);
        int added = Math.max(0, Math.min(amount, max - state.fuel));
        state.fuel += added;
        persistAsync(key, state);
        return added;
    }

    /**
     * Recalcule, en fonction du temps REEL ecoule depuis le dernier appel, combien de Lucky Blocks
     * ce generateur doit produire (1 par "intervalle-secondes" ecoulee), plafonne par le carburant
     * disponible. Decremente le carburant et avance l'horodatage EXACTEMENT du temps correspondant
     * aux Lucky Blocks reellement produits (le reste continue de s'accumuler pour le prochain
     * appel, rien n'est jamais perdu ni arrondi au superieur). Renvoie le nombre de Lucky Blocks a
     * produire (0 si pas encore assez de temps ecoule ou plus de carburant).
     */
    public int tick(Block block) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null || state.fuel <= 0) {
            return 0;
        }
        long effectiveInterval = getEffectiveIntervalSeconds(block);
        long now = System.currentTimeMillis();
        long elapsedSeconds = (now - state.lastTickMillis) / 1000L;
        long cycles = elapsedSeconds / effectiveInterval;
        if (cycles <= 0) {
            return 0;
        }
        int produced = (int) Math.min(cycles, state.fuel);
        state.fuel -= produced;
        state.lastTickMillis += produced * effectiveInterval * 1000L;
        persistAsync(key, state);
        return produced;
    }

    // ---- Recette de craft (produit uniquement la famille "famille-craft") ----

    public void registerRecipe() {
        unregisterRecipe();
        if (recipe.isEmpty()) {
            return;
        }
        LuckyBlockFamily family = luckyBlockManager.getFamily(craftFamilyId);
        if (family == null) {
            plugin.getLogger().warning("famille-craft '" + craftFamilyId + "' introuvable : "
                    + "le Generateur de Lucky Block ne sera pas craftable (give/recompense uniquement).");
            return;
        }
        try {
            ShapelessRecipe shapeless = new ShapelessRecipe(recipeKey, createItem(family, 1));
            for (RecipeIngredient ingredient : recipe) {
                shapeless.addIngredient(ingredient.amount(), ingredient.material());
            }
            Bukkit.addRecipe(shapeless);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Recette invalide pour le Generateur de Lucky Block : " + e.getMessage());
        }
    }

    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }
}
