package com.mysteriacraft.customitems.generator;

import com.mysteriacraft.core.RecipeIngredient;
import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.storage.Database;
import com.mysteriacraft.customitems.CustomItemDefinition;
import com.mysteriacraft.customitems.CustomItemManager;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * meme hors-ligne) jusqu'a un plafond de stockage. Clic-droit dessus (voir GeneratorService)
 * recupere tout l'argent stocke sur le solde du joueur.
 *
 * IMPORTANT : contrairement a un ItemStack ou une Entity, un Block "nu" (ex: IRON_ORE) n'a PAS de
 * PersistentDataContainer sur paper-api 1.20.1 (seuls les blocs avec tile entity, comme les
 * coffres, en ont un via leur BlockState). L'etat de chaque generateur pose (type, proprietaire,
 * stock, bonus de rythme, hologramme) est donc suivi via une table SQLite dediee indexee par
 * position, mise en cache memoire (write-through : chaque ecriture met a jour le cache puis
 * persiste en base de facon asynchrone).
 */
public class GeneratorManager {

    /** Nature de ce qu'un generateur produit : ARGENT (comportement historique, credite le solde
     * du proprietaire) ou OBJET (donne des exemplaires reels de "resultMaterial", voir "idee
     * generateur de fer/or/diamant qui genere du fer/or/diamant"). */
    public enum ResultType {
        ARGENT,
        OBJET
    }

    /** Un type de generateur : bloc, quantite generee par cycle complet (argent OU objets selon
     * resultType), duree du cycle, plafond de stockage, et sa recette de craft optionnelle (voir
     * "recette"/"generateur-precedent" dans generateurs.yml : plusieurs paliers de craft
     * progressifs vers le generateur legendaire).
     *
     * @param resultMaterial materiau vanilla donne par cycle (OBJET), ignore si resultCustomItemId
     *                       est defini.
     * @param resultCustomItemId si defini (OBJET), le generateur donne cet item CUSTOM (voir
     *                           custom_items.yml) au lieu d'un materiau vanilla, ex: un generateur
     *                           d'essence d'enchantement pour la Table d'Enchantement Custom. */
    public record GeneratorType(String id, String displayName, Material block, double amount,
                                 long intervalSeconds, double storageMax, boolean rewardOnly,
                                 List<RecipeIngredient> recipe, String requiresGeneratorId,
                                 ResultType resultType, Material resultMaterial, String resultCustomItemId) {
        /** Quantite generee par seconde reelle (avant bonus d'amelioration). */
        public double ratePerSecond() {
            return intervalSeconds > 0 ? amount / intervalSeconds : 0.0;
        }

        public boolean isCraftable() {
            return !recipe.isEmpty();
        }

        public boolean producesItems() {
            return resultType == ResultType.OBJET;
        }

        public boolean producesCustomItem() {
            return resultType == ResultType.OBJET && resultCustomItemId != null;
        }
    }

    /** Etat persiste d'un generateur pose, indexe par position. */
    private static final class GeneratorState {
        String typeId;
        UUID owner;
        double stock;
        long lastTickMillis;
        double bonusPercent;
        double storageBonusPercent;
        UUID hologramUuid;
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager generatorsConfig;
    private final CustomItemManager customItemManager;
    private final NamespacedKey generatorKey;
    private final NamespacedKey typeKey;
    private static final String RECIPE_KEY_PREFIX = "generateur-";

    /** Generateurs actuellement poses, indexes par position (charges au demarrage depuis la base). */
    private final Map<Location, GeneratorState> generators = new ConcurrentHashMap<>();

    private final Map<String, GeneratorType> types = new LinkedHashMap<>();
    private long tickSeconds = 5;
    private int maxPerPlayer = 30;
    private double taxPercent = 0;
    private String upgradeItemId = "boost_generateur";
    private double bonusPerUpgradePercent = 10;
    private double bonusMaxPercent = 50;
    private String storageUpgradeItemId = "boost_stockage_generateur";
    private double storageBonusPerUpgradePercent = 20;
    private double storageBonusMaxPercent = 100;

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0");
    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    public GeneratorManager(Plugin plugin, Database database, ConfigManager generatorsConfig,
                             CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.database = database;
        this.generatorsConfig = generatorsConfig;
        this.customItemManager = customItemManager;
        this.generatorKey = new NamespacedKey(plugin, "generateur");
        this.typeKey = new NamespacedKey(plugin, "generateur-type");
        createTable();
        loadGenerators();
        loadConfig();
        registerRecipes();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS generateurs (" +
                "monde TEXT NOT NULL, " +
                "x INTEGER NOT NULL, " +
                "y INTEGER NOT NULL, " +
                "z INTEGER NOT NULL, " +
                "type_id TEXT NOT NULL, " +
                "proprietaire TEXT, " +
                "stock REAL NOT NULL DEFAULT 0, " +
                "dernier_tick INTEGER NOT NULL DEFAULT 0, " +
                "bonus_rythme REAL NOT NULL DEFAULT 0, " +
                "hologramme_uuid TEXT, " +
                "bonus_stockage REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (monde, x, y, z)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'generateurs' : " + e.getMessage());
        }
        // Migration : une base creee AVANT l'ajout de l'amelioration de stockage n'a pas cette
        // colonne (CREATE TABLE IF NOT EXISTS ne la rajoute pas toute seule).
        try (PreparedStatement statement = connection.prepareStatement(
                "ALTER TABLE generateurs ADD COLUMN bonus_stockage REAL NOT NULL DEFAULT 0;")) {
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // Colonne deja presente : rien a faire.
        }
    }

    private void loadGenerators() {
        generators.clear();
        String select = "SELECT monde, x, y, z, type_id, proprietaire, stock, dernier_tick, "
                + "bonus_rythme, hologramme_uuid, bonus_stockage FROM generateurs;";
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
                state.typeId = rs.getString("type_id");
                String ownerRaw = rs.getString("proprietaire");
                if (ownerRaw != null) {
                    try {
                        state.owner = UUID.fromString(ownerRaw);
                    } catch (IllegalArgumentException ignored) {
                        // Proprietaire corrompu : le generateur reste utilisable mais sans auto-collecte/limite.
                    }
                }
                state.stock = rs.getDouble("stock");
                state.lastTickMillis = rs.getLong("dernier_tick");
                state.bonusPercent = rs.getDouble("bonus_rythme");
                state.storageBonusPercent = rs.getDouble("bonus_stockage");
                String hologramRaw = rs.getString("hologramme_uuid");
                if (hologramRaw != null) {
                    try {
                        state.hologramUuid = UUID.fromString(hologramRaw);
                    } catch (IllegalArgumentException ignored) {
                        // UUID corrompu : l'hologramme sera simplement recree au besoin.
                    }
                }
                generators.put(location, state);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur chargement des generateurs poses : " + e.getMessage());
        }
        plugin.getLogger().info(generators.size() + " Generateur(s) d'Argent rechargee(s) depuis la base.");
    }

    private void persistAsync(Location location, GeneratorState state) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String upsert = "INSERT INTO generateurs (monde, x, y, z, type_id, proprietaire, stock, "
                    + "dernier_tick, bonus_rythme, hologramme_uuid, bonus_stockage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(monde, x, y, z) DO UPDATE SET type_id = excluded.type_id, "
                    + "proprietaire = excluded.proprietaire, stock = excluded.stock, "
                    + "dernier_tick = excluded.dernier_tick, bonus_rythme = excluded.bonus_rythme, "
                    + "hologramme_uuid = excluded.hologramme_uuid, bonus_stockage = excluded.bonus_stockage;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, location.getWorld().getName());
                statement.setInt(2, location.getBlockX());
                statement.setInt(3, location.getBlockY());
                statement.setInt(4, location.getBlockZ());
                statement.setString(5, state.typeId);
                statement.setString(6, state.owner != null ? state.owner.toString() : null);
                statement.setDouble(7, state.stock);
                statement.setLong(8, state.lastTickMillis);
                statement.setDouble(9, state.bonusPercent);
                statement.setString(10, state.hologramUuid != null ? state.hologramUuid.toString() : null);
                statement.setDouble(11, state.storageBonusPercent);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur sauvegarde generateur : " + e.getMessage());
            }
        });
    }

    private void deleteAsync(Location location) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String delete = "DELETE FROM generateurs WHERE monde = ? AND x = ? AND y = ? AND z = ?;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                statement.setString(1, location.getWorld().getName());
                statement.setInt(2, location.getBlockX());
                statement.setInt(3, location.getBlockY());
                statement.setInt(4, location.getBlockZ());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur suppression generateur : " + e.getMessage());
            }
        });
    }

    /** Cle de position (bloc entier, sans decimales) utilisee pour indexer generators. */
    private static Location blockKey(Block block) {
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
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

                List<RecipeIngredient> recipe = new ArrayList<>();
                for (Map<?, ?> raw : section.getMapList("recette")) {
                    Material ingredientMaterial = Material.matchMaterial(String.valueOf(raw.get("materiel")));
                    if (ingredientMaterial == null) {
                        continue;
                    }
                    int ingredientAmount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
                    recipe.add(new RecipeIngredient(ingredientMaterial, Math.max(1, ingredientAmount)));
                }
                String requiresGeneratorId = section.contains("generateur-precedent")
                        ? section.getString("generateur-precedent").toLowerCase() : null;

                ResultType resultType;
                try {
                    resultType = ResultType.valueOf(section.getString("type-resultat", "ARGENT").toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("type-resultat invalide pour le generateur '" + id + "', ARGENT utilise.");
                    resultType = ResultType.ARGENT;
                }
                Material resultMaterial = null;
                String resultCustomItemId = null;
                if (resultType == ResultType.OBJET) {
                    if (section.contains("objet-resultat-custom")) {
                        resultCustomItemId = section.getString("objet-resultat-custom").toLowerCase();
                    } else {
                        resultMaterial = Material.matchMaterial(section.getString("objet-resultat", "IRON_INGOT"));
                        if (resultMaterial == null) {
                            plugin.getLogger().warning("objet-resultat invalide pour le generateur '" + id + "', IRON_INGOT utilise.");
                            resultMaterial = Material.IRON_INGOT;
                        }
                    }
                }

                types.put(id.toLowerCase(), new GeneratorType(
                        id.toLowerCase(), displayName, block, amount, intervalSeconds, storageMax, rewardOnly,
                        recipe, requiresGeneratorId, resultType, resultMaterial, resultCustomItemId));
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

        ConfigurationSection ameliorationStockage = generatorsConfig.get().getConfigurationSection("amelioration-stockage");
        if (ameliorationStockage != null) {
            storageUpgradeItemId = ameliorationStockage.getString("item-id", "boost_stockage_generateur").toLowerCase();
            storageBonusPerUpgradePercent = ameliorationStockage.getDouble("bonus-par-amelioration", 20.0);
            storageBonusMaxPercent = ameliorationStockage.getDouble("bonus-max", 100.0);
        }

        plugin.getLogger().info("Generateurs d'argent : " + types.size() + " type(s) charge(s), tick toutes les "
                + tickSeconds + "s, max " + maxPerPlayer + " par joueur, taxe " + taxPercent + "%.");
    }

    // ---- Recettes de craft (progression de paliers vers le generateur legendaire) ----

    /** (Re)enregistre les recettes de craft de chaque type de generateur qui en declare une (voir
     * "recette"/"generateur-precedent" dans generateurs.yml). Retire d'abord l'ancienne recette de
     * chaque type (meme ceux qui n'en ont plus), pour qu'un rechargement de config qui supprime
     * une recette la desenregistre bien du jeu. */
    public void registerRecipes() {
        unregisterRecipes();
        for (GeneratorType type : types.values()) {
            if (!type.isCraftable()) {
                continue;
            }
            // Construction de la recette protegee de bout en bout (shape/ingredients compris, pas
            // seulement Bukkit.addRecipe()) : une recette mal configuree dans generateurs.yml ne
            // doit jamais empecher le plugin entier de demarrer (meme pattern que LuckyBlockManager
            // et CustomItemManager).
            try {
                NamespacedKey key = recipeKey(type.id());
                ShapelessRecipe recipe = new ShapelessRecipe(key, createGeneratorItem(type));
                for (RecipeIngredient ingredient : type.recipe()) {
                    recipe.addIngredient(ingredient.amount(), ingredient.material());
                }
                if (type.requiresGeneratorId() != null) {
                    GeneratorType previous = getType(type.requiresGeneratorId());
                    if (previous == null) {
                        plugin.getLogger().warning("generateur-precedent inconnu pour le generateur '"
                                + type.id() + "' : " + type.requiresGeneratorId());
                        continue;
                    }
                    recipe.addIngredient(new RecipeChoice.ExactChoice(createGeneratorItem(previous)));
                }
                Bukkit.addRecipe(recipe);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("Recette invalide pour le generateur '" + type.id() + "' : " + e.getMessage());
            }
        }
    }

    public void unregisterRecipes() {
        for (String typeId : types.keySet()) {
            Bukkit.removeRecipe(recipeKey(typeId));
        }
    }

    private NamespacedKey recipeKey(String typeId) {
        return new NamespacedKey(plugin, RECIPE_KEY_PREFIX + typeId);
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
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? 0.0 : state.bonusPercent;
    }

    /** Ajoute du bonus de rythme (plafonne a bonus-max). Renvoie le nouveau total (0 si ce bloc
     * n'est pas/plus un generateur connu). */
    public double addBonusPercent(Block block, double amount) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null) {
            return 0.0;
        }
        state.bonusPercent = Math.min(bonusMaxPercent, state.bonusPercent + amount);
        persistAsync(key, state);
        return state.bonusPercent;
    }

    /** Argent genere par seconde reelle pour CE bloc, bonus d'amelioration inclus. */
    public double getEffectiveRatePerSecond(Block block) {
        GeneratorType type = getBlockType(block);
        if (type == null) {
            return 0.0;
        }
        return type.ratePerSecond() * (1.0 + getBonusPercent(block) / 100.0);
    }

    // ---- Amelioration de stockage (bonus incremental de plafond par generateur) ----

    public String getStorageUpgradeItemId() {
        return storageUpgradeItemId;
    }

    public double getStorageBonusPerUpgradePercent() {
        return storageBonusPerUpgradePercent;
    }

    public double getStorageBonusMaxPercent() {
        return storageBonusMaxPercent;
    }

    public double getStorageBonusPercent(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? 0.0 : state.storageBonusPercent;
    }

    /** Ajoute du bonus de stockage (plafonne a bonus-max). Renvoie le nouveau total (0 si ce bloc
     * n'est pas/plus un generateur connu). */
    public double addStorageBonusPercent(Block block, double amount) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null) {
            return 0.0;
        }
        state.storageBonusPercent = Math.min(storageBonusMaxPercent, state.storageBonusPercent + amount);
        persistAsync(key, state);
        return state.storageBonusPercent;
    }

    /** Plafond de stockage effectif pour CE bloc, bonus d'amelioration de stockage inclus. */
    public double getEffectiveStorageMax(Block block) {
        GeneratorType type = getBlockType(block);
        if (type == null) {
            return 0.0;
        }
        return type.storageMax() * (1.0 + getStorageBonusPercent(block) / 100.0);
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
            List<String> lore;
            if (type.producesItems()) {
                String materialName;
                if (type.producesCustomItem()) {
                    CustomItemDefinition customDefinition = customItemManager.getItem(type.resultCustomItemId());
                    materialName = customDefinition != null ? customDefinition.displayName() : type.resultCustomItemId();
                } else {
                    materialName = type.resultMaterial().name().replace('_', ' ');
                }
                lore = new ArrayList<>(List.of(
                        MessageManager.color("&7Genere automatiquement des &f" + materialName + "&7,"),
                        MessageManager.color("&7meme hors-ligne."),
                        MessageManager.color("&7Rythme : &e~" + NUMBER_FORMAT.format(perHour) + "&7/heure"),
                        MessageManager.color("&7Stockage max : &e" + NUMBER_FORMAT.format(type.storageMax())),
                        MessageManager.color("&7Clic-droit pour recuperer les objets stockes.")
                ));
            } else {
                lore = new ArrayList<>(List.of(
                        MessageManager.color("&7Genere de l'argent automatiquement,"),
                        MessageManager.color("&7meme hors-ligne."),
                        MessageManager.color("&7Rythme : &e~" + NUMBER_FORMAT.format(perHour) + "&7/heure"),
                        MessageManager.color("&7Stockage max : &e" + NUMBER_FORMAT.format(type.storageMax())),
                        MessageManager.color("&7Clic-droit pour recuperer l'argent stocke.")
                ));
            }
            if (type.rewardOnly()) {
                lore.add(MessageManager.color("&5Uniquement obtenable en recompense"));
                lore.add(MessageManager.color("&5(battlepass, quete, luckyblock)."));
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

        Location key = blockKey(block);
        GeneratorState state = new GeneratorState();
        state.typeId = type.id();
        state.owner = owner;
        state.stock = 0.0;
        state.lastTickMillis = System.currentTimeMillis();
        generators.put(key, state);
        persistAsync(key, state);
    }

    /** A appeler quand un generateur est casse, pour arreter son suivi (accumulation/hologramme). */
    public void forgetGenerator(Location location) {
        generators.remove(location);
        deleteAsync(location);
    }

    public Set<Location> getActiveGeneratorLocations() {
        return generators.keySet();
    }

    public boolean isGeneratorBlock(Block block) {
        return generators.containsKey(blockKey(block));
    }

    /** Type de generateur de ce bloc, ou null si non marque ou si son type a disparu de la config. */
    public GeneratorType getBlockType(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? null : getType(state.typeId);
    }

    /** UUID du proprietaire de ce generateur (celui qui l'a pose), ou null si inconnu. */
    public UUID getOwner(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? null : state.owner;
    }

    /** Nombre de generateurs actuellement suivis appartenant a ce joueur (charges au demarrage +
     * poses depuis). */
    public int countOwnedGenerators(UUID owner) {
        int count = 0;
        for (GeneratorState state : generators.values()) {
            if (owner.equals(state.owner)) {
                count++;
            }
        }
        return count;
    }

    /** Bloc conteneur (coffre, coffre piege, tonneau ou hopper) colle a une face de ce generateur,
     * ou null. Coller un conteneur active l'auto-collecte : l'argent genere est credite en continu
     * au proprietaire sans clic-droit (pour un generateur OBJET, les objets sont deposes dedans). */
    public Block getAdjacentHopper(Block generatorBlock) {
        for (BlockFace face : ADJACENT_FACES) {
            Block relative = generatorBlock.getRelative(face);
            Material material = relative.getType();
            if (material == Material.HOPPER || material == Material.CHEST
                    || material == Material.TRAPPED_CHEST || material == Material.BARREL) {
                return relative;
            }
        }
        return null;
    }

    // ---- Stock d'argent (accumule en continu), stocke par position ----

    public double getStored(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? 0.0 : state.stock;
    }

    public void setStored(Block block, double amount) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null) {
            return;
        }
        state.stock = Math.max(0, amount);
        persistAsync(key, state);
    }

    public long getLastTickMillis(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? System.currentTimeMillis() : state.lastTickMillis;
    }

    public void setLastTickMillis(Block block, long millis) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null) {
            return;
        }
        state.lastTickMillis = millis;
        persistAsync(key, state);
    }

    /** Recalcule le stock en fonction du temps ecoule reel depuis le dernier tick (rythme effectif,
     * bonus d'amelioration inclus), plafonne a storage-max, et avance l'horodatage. Renvoie le
     * nouveau stock. Aucun effet si le type est inconnu. */
    public double accrue(Block block) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        GeneratorType type = state == null ? null : getType(state.typeId);
        if (state == null || type == null) {
            return state == null ? 0.0 : state.stock;
        }
        long now = System.currentTimeMillis();
        double elapsedSeconds = Math.max(0, (now - state.lastTickMillis) / 1000.0);

        state.stock = Math.min(getEffectiveStorageMax(block), state.stock + elapsedSeconds * getEffectiveRatePerSecond(block));
        state.lastTickMillis = now;
        persistAsync(key, state);
        return state.stock;
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

    /** Recalcule le stock (accrue) puis n'en retire que la partie ENTIERE (un generateur OBJET ne
     * peut donner que des exemplaires complets) : la partie fractionnaire reste accumulee pour le
     * prochain cycle, rien n'est jamais perdu ni arrondi au superieur. Renvoie le nombre entier
     * d'exemplaires recuperes (0 si moins d'un exemplaire complet n'est encore accumule). */
    public int collectWholeUnits(Block block) {
        double stored = accrue(block);
        int whole = (int) Math.floor(stored);
        if (whole <= 0) {
            return 0;
        }
        setStored(block, stored - whole);
        return whole;
    }

    // ---- Nettoyage d'un ancien hologramme (fonctionnalite retiree : plus d'ArmorStand affiche
    // au-dessus des generateurs) ----

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
}
