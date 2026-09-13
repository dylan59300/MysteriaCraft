package com.mysteriacraft.luckyblock;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Charge les familles de Lucky Block depuis luckyblocks.yml, gere le cooldown par joueur et
 * par famille (SQLite), le tirage pondere des effets, la fabrication de l'item (avec sa marque
 * PersistentDataContainer) et l'enregistrement/desenregistrement des recettes de craft.
 */
public class LuckyBlockManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager luckyBlocksConfig;
    private final NamespacedKey familyKey;

    private final Map<String, LuckyBlockFamily> families = new LinkedHashMap<>();

    public LuckyBlockManager(Plugin plugin, Database database, ConfigManager luckyBlocksConfig) {
        this.plugin = plugin;
        this.database = database;
        this.luckyBlocksConfig = luckyBlocksConfig;
        this.familyKey = new NamespacedKey(plugin, "luckyblock-famille");
        createTable();
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

    public void loadFamilies() {
        families.clear();
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

    private LuckyBlockFamily parseFamily(String id, ConfigurationSection section) {
        String displayName = section.getString("nom", id);
        Material material = Material.matchMaterial(section.getString("materiel", "GOLD_BLOCK"));
        if (material == null) {
            material = Material.GOLD_BLOCK;
        }
        int order = section.getInt("ordre", 0);
        long cooldownSeconds = section.getLong("cooldown-secondes", 30);
        double buyPrice = section.getDouble("prix-achat", 0);

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

        return new LuckyBlockFamily(id, displayName, material, order, cooldownSeconds, buyPrice, recipe, effects);
    }

    @SuppressWarnings("unchecked")
    private LuckyBlockEffect parseEffect(Map<?, ?> raw) {
        EffectKind kind = EffectKind.fromString(String.valueOf(raw.getOrDefault("type", "BON")));
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

        BadEffectType badType = BadEffectType.fromString(String.valueOf(raw.getOrDefault("action", "TNT")));
        return switch (badType) {
            case TNT -> {
                int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
                yield new LuckyBlockEffect(EffectKind.MAUVAIS, chance, "TNT x" + amount, null, badType, null, amount, 0);
            }
            case MOBS -> {
                String mob = String.valueOf(raw.getOrDefault("mob", "ZOMBIE"));
                int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
                yield new LuckyBlockEffect(EffectKind.MAUVAIS, chance, amount + "x " + mob, null, badType, mob, amount, 0);
            }
            case POTION -> {
                String potionType = String.valueOf(raw.getOrDefault("effet-potion", "POISON"));
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

    /** Tirage pondere d'un effet parmi le pool (bons + mauvais) d'une famille. */
    public LuckyBlockEffect pickEffect(LuckyBlockFamily family) {
        double totalWeight = family.totalWeight();
        if (totalWeight <= 0 || family.effects().isEmpty()) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (LuckyBlockEffect effect : family.effects()) {
            cumulative += effect.chance();
            if (roll < cumulative) {
                return effect;
            }
        }
        return family.effects().get(family.effects().size() - 1);
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

    /** Marque un bloc pose comme etant un Lucky Block de cette famille (PersistentDataContainer, extension Paper sur Block). */
    public void tagBlock(Block block, String familyId) {
        block.getPersistentDataContainer().set(familyKey, PersistentDataType.STRING, familyId);
    }

    /** Renvoie la famille d'un bloc pose, ou null si ce n'est pas un Lucky Block marque. */
    public LuckyBlockFamily getFamilyOfBlock(Block block) {
        String id = block.getPersistentDataContainer().get(familyKey, PersistentDataType.STRING);
        return getFamily(id);
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
