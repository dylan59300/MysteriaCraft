package com.mysteriacraft.luckyblock;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Charge les familles de Lucky Block depuis luckyblocks.yml, gere le cooldown par joueur et
 * par famille (SQLite), le tirage pondere des effets, la fabrication de l'item (avec sa marque
 * PersistentDataContainer, valable pour un ItemStack) et l'enregistrement/desenregistrement des
 * recettes de craft.
 *
 * IMPORTANT : contrairement a un ItemStack ou une Entity, un Block "nu" (ex: GOLD_BLOCK) n'a PAS
 * de PersistentDataContainer sur paper-api 1.20.1 (seuls les blocs avec tile entity, comme les
 * coffres, en ont un via leur BlockState). L'etat par bloc (famille, bonus de minerais) est donc
 * suivi via une table SQLite dediee, indexee par position, mise en cache memoire (write-through :
 * chaque ecriture met a jour le cache puis persiste en base de facon asynchrone).
 */
public class LuckyBlockManager {

    /** Etat d'un Lucky Block pose : sa famille et son bonus de minerais accumule. */
    private static final class PlacedBlockState {
        String familyId;
        double bonus;

        PlacedBlockState(String familyId, double bonus) {
            this.familyId = familyId;
            this.bonus = bonus;
        }
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager luckyBlocksConfig;
    private final NamespacedKey familyKey;

    private final Map<String, LuckyBlockFamily> families = new LinkedHashMap<>();
    private final Map<Material, Double> oreBonuses = new LinkedHashMap<>();
    private double bonusMax = 45.0;

    /** Lucky Blocks actuellement poses dans le monde, indexes par position (charge au demarrage). */
    private final Map<Location, PlacedBlockState> placedBlocks = new ConcurrentHashMap<>();

    public LuckyBlockManager(Plugin plugin, Database database, ConfigManager luckyBlocksConfig) {
        this.plugin = plugin;
        this.database = database;
        this.luckyBlocksConfig = luckyBlocksConfig;
        this.familyKey = new NamespacedKey(plugin, "luckyblock-famille");
        createTable();
        createBlocksTable();
        loadPlacedBlocks();
        loadFamilies();
        registerRecipes();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS luckyblock_cooldowns (" +
                "uuid TEXT NOT NULL, " +
                "famille_id TEXT NOT NULL, " +
                "derniere_utilisation INTEGER NOT NULL, " +
                "PRIMARY KEY (uuid, famille_id)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'luckyblock_cooldowns' : " + e.getMessage());
        }
    }

    private void createBlocksTable() {
        String sql = "CREATE TABLE IF NOT EXISTS luckyblock_blocks (" +
                "monde TEXT NOT NULL, " +
                "x INTEGER NOT NULL, " +
                "y INTEGER NOT NULL, " +
                "z INTEGER NOT NULL, " +
                "famille_id TEXT NOT NULL, " +
                "bonus REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (monde, x, y, z)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'luckyblock_blocks' : " + e.getMessage());
        }
    }

    private void loadPlacedBlocks() {
        placedBlocks.clear();
        String select = "SELECT monde, x, y, z, famille_id, bonus FROM luckyblock_blocks;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                org.bukkit.World world = Bukkit.getWorld(rs.getString("monde"));
                if (world == null) {
                    continue;
                }
                Location location = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                placedBlocks.put(location, new PlacedBlockState(rs.getString("famille_id"), rs.getDouble("bonus")));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur chargement des Lucky Blocks poses : " + e.getMessage());
        }
        plugin.getLogger().info(placedBlocks.size() + " Lucky Block(s) pose(s) recharge(s) depuis la base.");
    }

    private void persistBlockAsync(Location location, PlacedBlockState state) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String upsert = "INSERT INTO luckyblock_blocks (monde, x, y, z, famille_id, bonus) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(monde, x, y, z) DO UPDATE SET famille_id = excluded.famille_id, bonus = excluded.bonus;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, location.getWorld().getName());
                statement.setInt(2, location.getBlockX());
                statement.setInt(3, location.getBlockY());
                statement.setInt(4, location.getBlockZ());
                statement.setString(5, state.familyId);
                statement.setDouble(6, state.bonus);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur sauvegarde Lucky Block pose : " + e.getMessage());
            }
        });
    }

    private void deleteBlockAsync(Location location) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String delete = "DELETE FROM luckyblock_blocks WHERE monde = ? AND x = ? AND y = ? AND z = ?;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                statement.setString(1, location.getWorld().getName());
                statement.setInt(2, location.getBlockX());
                statement.setInt(3, location.getBlockY());
                statement.setInt(4, location.getBlockZ());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur suppression Lucky Block pose : " + e.getMessage());
            }
        });
    }

    /** Cle de position (bloc entier, sans decimales) utilisee pour indexer placedBlocks. */
    private static Location blockKey(Block block) {
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    public void loadFamilies() {
        families.clear();
        loadOreBonuses();

        ConfigurationSection root = luckyBlocksConfig.get().getConfigurationSection("familles");
        if (root == null) {
            plugin.getLogger().warning("Aucune famille de Lucky Block trouvee (section 'familles' manquante).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                families.put(id.toLowerCase(), parseFamily(id, section));
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement famille Lucky Block '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(families.size() + " famille(s) de Lucky Block chargee(s).");
    }

    private void loadOreBonuses() {
        oreBonuses.clear();
        bonusMax = luckyBlocksConfig.get().getDouble("bonus-minerais-max", 45.0);

        ConfigurationSection section = luckyBlocksConfig.get().getConfigurationSection("bonus-minerais");
        if (section == null) {
            return;
        }
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Materiau inconnu dans bonus-minerais : " + materialName);
                continue;
            }
            oreBonuses.put(material, section.getDouble(materialName, 0));
        }
    }

    private LuckyBlockFamily parseFamily(String id, ConfigurationSection section) {
        String displayName = section.getString("nom", id);
        Material material = Material.matchMaterial(section.getString("materiel", "GOLD_BLOCK"));
        if (material == null) {
            material = Material.GOLD_BLOCK;
        }
        int order = section.getInt("ordre", 0);
        long cooldownSeconds = section.getLong("cooldown-secondes", 30);
        double buyPrice = section.getDouble("prix-achat", 0);
        double baseGoodChance = section.getDouble("chance-bonne-base", 50.0);

        List<RecipeIngredient> recipe = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("recette")) {
            Material ingredientMaterial = Material.matchMaterial(String.valueOf(raw.get("materiel")));
            if (ingredientMaterial == null) {
                continue;
            }
            int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
            recipe.add(new RecipeIngredient(ingredientMaterial, Math.max(1, amount)));
        }

        List<LuckyBlockEffect> effects = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("effets")) {
            LuckyBlockEffect effect = parseEffect(raw);
            if (effect != null) {
                effects.add(effect);
            }
        }

        return new LuckyBlockFamily(id, displayName, material, order, cooldownSeconds, buyPrice, baseGoodChance, recipe, effects);
    }

    /** Meme limitation que dans CrateManager : getOrDefault(k, "texte") ne compile pas sur une
     * Map&lt;?, ?&gt; (capture de type inconnue). On relit la valeur brute nous-memes. */
    private static String getOrDefault(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : fallback;
    }

    @SuppressWarnings("unchecked")
    private LuckyBlockEffect parseEffect(Map<?, ?> raw) {
        EffectKind kind = EffectKind.fromString(getOrDefault(raw, "type", "BON"));
        double chance = raw.containsKey("chance") ? Double.parseDouble(String.valueOf(raw.get("chance"))) : 1.0;

        if (kind == EffectKind.BON) {
            Object rewardRaw = raw.get("recompense");
            ConfigurationSection rewardSection = mapToSection((Map<String, Object>) rewardRaw);
            Reward reward = RewardParser.parse(rewardSection);
            if (reward == null) {
                return null;
            }
            return new LuckyBlockEffect(EffectKind.BON, chance, reward.displayName(), reward, null, null, 0, 0);
        }

        BadEffectType badType = BadEffectType.fromString(getOrDefault(raw, "action", "TNT"));
        return switch (badType) {
            case TNT -> {
                int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
                yield new LuckyBlockEffect(EffectKind.MAUVAIS, chance, "TNT x" + amount, null, badType, null, amount, 0);
            }
            case MOBS -> {
                String mob = getOrDefault(raw, "mob", "ZOMBIE");
                int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
                yield new LuckyBlockEffect(EffectKind.MAUVAIS, chance, amount + "x " + mob, null, badType, mob, amount, 0);
            }
            case POTION -> {
                String potionType = getOrDefault(raw, "effet-potion", "POISON");
                long durationSeconds = raw.containsKey("duree-secondes") ? Long.parseLong(String.valueOf(raw.get("duree-secondes"))) : 5L;
                int amplifier = raw.containsKey("amplificateur") ? Integer.parseInt(String.valueOf(raw.get("amplificateur"))) : 0;
                yield new LuckyBlockEffect(EffectKind.MAUVAIS, chance, potionType, null, badType, potionType,
                        (int) (durationSeconds * 20), amplifier);
            }
            case FOUDRE -> new LuckyBlockEffect(EffectKind.MAUVAIS, chance, "Foudre", null, badType, null, 0, 0);
        };
    }

    /** Convertit une Map issue de getMapList (loot Crates/LuckyBlock) en ConfigurationSection pour reutiliser RewardParser. */
    private ConfigurationSection mapToSection(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        MemoryConfiguration memoryConfig = new MemoryConfiguration();
        ConfigurationSection section = memoryConfig.createSection("recompense");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            section.set(entry.getKey(), entry.getValue());
        }
        return section;
    }

    public List<LuckyBlockFamily> getFamiliesSorted() {
        List<LuckyBlockFamily> sorted = new ArrayList<>(families.values());
        sorted.sort(Comparator.comparingInt(LuckyBlockFamily::order));
        return sorted;
    }

    public LuckyBlockFamily getFamily(String id) {
        return id == null ? null : families.get(id.toLowerCase());
    }

    /**
     * Tirage en 2 etapes : d'abord BON ou MAUVAIS selon la chance de base de la famille
     * (+ bonus de minerais places a cote du bloc, plafonne), puis tirage pondere d'un effet
     * precis au sein du pool correspondant (BON ou MAUVAIS).
     */
    public LuckyBlockEffect pickEffect(LuckyBlockFamily family, double bonusPercent) {
        if (family.effects().isEmpty()) {
            return null;
        }
        double goodChance = Math.min(95.0, Math.max(0.0, family.baseGoodChance() + bonusPercent));
        boolean rollGood = ThreadLocalRandom.current().nextDouble(100.0) < goodChance;

        List<LuckyBlockEffect> pool = rollGood ? family.goodEffects() : family.badEffects();
        if (pool.isEmpty()) {
            // Repli sur l'autre pool si celui tire est vide (ex: famille sans effet MAUVAIS configure).
            pool = rollGood ? family.badEffects() : family.goodEffects();
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pickWeighted(pool);
    }

    private LuckyBlockEffect pickWeighted(List<LuckyBlockEffect> pool) {
        double totalWeight = 0;
        for (LuckyBlockEffect effect : pool) {
            totalWeight += effect.chance();
        }
        if (totalWeight <= 0) {
            return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (LuckyBlockEffect effect : pool) {
            cumulative += effect.chance();
            if (roll < cumulative) {
                return effect;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // ---- Bonus de minerais (augmente la chance d'effet BON d'un bloc pose) ----

    public double getOreBonus(Material material) {
        return oreBonuses.getOrDefault(material, 0.0);
    }

    public boolean isBonusOre(Material material) {
        return oreBonuses.containsKey(material);
    }

    public double getBonus(Block block) {
        PlacedBlockState state = placedBlocks.get(blockKey(block));
        return state == null ? 0.0 : state.bonus;
    }

    /** Ajoute un bonus au bloc (plafonne a bonus-minerais-max). Renvoie le nouveau total (0 si ce
     * bloc n'est pas/plus un Lucky Block connu). */
    public double addBonus(Block block, double amount) {
        PlacedBlockState state = placedBlocks.get(blockKey(block));
        if (state == null) {
            return 0.0;
        }
        state.bonus = Math.min(bonusMax, state.bonus + amount);
        persistBlockAsync(blockKey(block), state);
        return state.bonus;
    }

    public double getBonusMax() {
        return bonusMax;
    }

    /** Somme les bonus de tous les minerais deja presents sur les 6 faces d'un bloc (utilise a la pose d'un Lucky Block). */
    public double computeSurroundingOreBonus(Block block) {
        double total = 0;
        for (BlockFace face : new BlockFace[]{
                BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            total += getOreBonus(block.getRelative(face).getType());
        }
        return total;
    }

    // ---- Item / marquage du bloc ----

    public ItemStack createItem(LuckyBlockFamily family) {
        return createItem(family, 1);
    }

    public ItemStack createItem(LuckyBlockFamily family, int amount) {
        ItemStack item = new ItemBuilder(family.blockMaterial(), amount)
                .name(family.displayName())
                .lore(List.of("&7Placez-le, puis cassez-le", "&7pour declencher un effet aleatoire !"))
                .build();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(familyKey, PersistentDataType.STRING, family.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Renvoie l'id de famille marque sur l'item (via craft/achat/recompense), ou null si ce n'est pas un Lucky Block. */
    public String getFamilyIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(familyKey, PersistentDataType.STRING);
    }

    /** Marque un bloc pose comme etant un Lucky Block de cette famille (suivi par position, voir
     * la table 'luckyblock_blocks' : un Block "nu" n'a pas de PersistentDataContainer propre). */
    public void tagBlock(Block block, String familyId) {
        Location key = blockKey(block);
        PlacedBlockState state = new PlacedBlockState(familyId, 0.0);
        placedBlocks.put(key, state);
        persistBlockAsync(key, state);
    }

    /** A appeler quand un Lucky Block est reellement retire du monde (casse non annulee), pour
     * ne pas laisser une position marquee indefiniment. */
    public void untagBlock(Block block) {
        Location key = blockKey(block);
        placedBlocks.remove(key);
        deleteBlockAsync(key);
    }

    /** Renvoie la famille d'un bloc pose, ou null si ce n'est pas un Lucky Block marque. */
    public LuckyBlockFamily getFamilyOfBlock(Block block) {
        PlacedBlockState state = placedBlocks.get(blockKey(block));
        return state == null ? null : getFamily(state.familyId);
    }

    // ---- Recettes de craft ----

    public void registerRecipes() {
        unregisterRecipes();
        for (LuckyBlockFamily family : families.values()) {
            if (family.recipe().isEmpty()) {
                continue;
            }
            NamespacedKey recipeKey = recipeKey(family.id());
            ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, createItem(family));
            for (RecipeIngredient ingredient : family.recipe()) {
                recipe.addIngredient(ingredient.amount(), ingredient.material());
            }
            Bukkit.addRecipe(recipe);
        }
    }

    public void unregisterRecipes() {
        for (String familyId : families.keySet()) {
            Bukkit.removeRecipe(recipeKey(familyId));
        }
    }

    private NamespacedKey recipeKey(String familyId) {
        return new NamespacedKey(plugin, "luckyblock-" + familyId.toLowerCase());
    }

    // ---- Cooldown par joueur et par famille ----

    public synchronized long getLastUsed(UUID uuid, String familyId) {
        String select = "SELECT derniere_utilisation FROM luckyblock_cooldowns WHERE uuid = ? AND famille_id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, familyId.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("derniere_utilisation");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture cooldown Lucky Block '" + familyId + "' pour " + uuid + " : " + e.getMessage());
        }
        return 0L;
    }

    public synchronized void markUsed(UUID uuid, String familyId) {
        String upsert = "INSERT INTO luckyblock_cooldowns (uuid, famille_id, derniere_utilisation) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, famille_id) DO UPDATE SET derniere_utilisation = excluded.derniere_utilisation;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, familyId.toLowerCase());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour cooldown Lucky Block '" + familyId + "' pour " + uuid + " : " + e.getMessage());
        }
    }
}
