package com.mysteriacraft.luckyblock;

import com.mysteriacraft.core.RecipeIngredient;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Charge les familles de Lucky Block depuis luckyblocks.yml (aucun cooldown : se recasse
 * immediatement), le tirage pondere des effets, la fabrication de l'item (avec sa marque
 * PersistentDataContainer, valable pour un ItemStack) et l'enregistrement/desenregistrement des
 * recettes de craft.
 *
 * IMPORTANT : contrairement a un ItemStack ou une Entity, un Block "nu" (ex: GOLD_BLOCK) n'a PAS
 * de PersistentDataContainer sur paper-api 1.21.2 (seuls les blocs avec tile entity, comme les
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
    /** Pool d'effets BONS commun a TOUTES les familles (voir luckyblocks.yml: effets-communs-bons),
     * y compris celles ajoutees plus tard : fusionne dans chaque famille au chargement. */
    private final List<LuckyBlockEffect> sharedGoodEffects = new ArrayList<>();

    /** Lucky Blocks actuellement poses dans le monde, indexes par position (charge au demarrage). */
    private final Map<Location, PlacedBlockState> placedBlocks = new ConcurrentHashMap<>();

    /** Systeme de pity : nombre de casses CONSECUTIVES sans effet "pity" (gros lot), par joueur et
     * par famille (remis a 0 des qu'un effet pity est obtenu, force ou naturel). En memoire
     * uniquement : repart a 0 au redemarrage du serveur (meme limitation connue que les autres
     * compteurs "depuis le dernier demarrage" de MysteriaCraft). */
    private final Map<UUID, Map<String, Integer>> pityCounters = new ConcurrentHashMap<>();
    /** Nombre de casses sans "gros lot" avant garantie (0 = systeme de pity desactive). */
    private int pityThreshold = 0;

    /** Kit de connexion (voir luckyblocks.yml: kit-connexion) : donne "quantite" exemplaires de
     * CHAQUE famille actuellement active a un joueur a chaque connexion. */
    private boolean joinKitEnabled = false;
    private int joinKitQuantity = 0;

    public LuckyBlockManager(Plugin plugin, Database database, ConfigManager luckyBlocksConfig) {
        this.plugin = plugin;
        this.database = database;
        this.luckyBlocksConfig = luckyBlocksConfig;
        this.familyKey = new NamespacedKey(plugin, "luckyblock-famille");
        createBlocksTable();
        createJoinKitTable();
        loadPlacedBlocks();
        loadFamilies();
        registerRecipes();
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

    // ---- Kit de connexion (voir luckyblocks.yml: kit-connexion) : limite a 1 fois par jour ----

    private void createJoinKitTable() {
        String sql = "CREATE TABLE IF NOT EXISTS luckyblock_kit_connexion (" +
                "uuid TEXT NOT NULL PRIMARY KEY, " +
                "derniere_date TEXT NOT NULL" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'luckyblock_kit_connexion' : " + e.getMessage());
        }
    }

    /** true si ce joueur a deja recu le kit de connexion aujourd'hui (voir onJoin dans LuckyBlockListener). */
    public boolean hasReceivedJoinKitToday(UUID playerId) {
        String select = "SELECT derniere_date FROM luckyblock_kit_connexion WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return LocalDate.now().toString().equals(rs.getString("derniere_date"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture kit de connexion pour " + playerId + " : " + e.getMessage());
        }
        return false;
    }

    /** Marque ce joueur comme ayant recu le kit de connexion aujourd'hui. */
    public void markJoinKitReceivedToday(UUID playerId) {
        String upsert = "INSERT INTO luckyblock_kit_connexion (uuid, derniere_date) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET derniere_date = excluded.derniere_date;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, LocalDate.now().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur sauvegarde kit de connexion pour " + playerId + " : " + e.getMessage());
        }
    }

    /** Cle de position (bloc entier, sans decimales) utilisee pour indexer placedBlocks. */
    private static Location blockKey(Block block) {
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    public void loadFamilies() {
        families.clear();
        loadOreBonuses();
        loadSharedGoodEffects();

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

    /**
     * Charge le pool d'effets BONS partage par toutes les familles (luckyblocks.yml:
     * effets-communs-bons). Fusionne automatiquement dans CHAQUE famille au parsing (voir
     * parseFamily), y compris les familles ajoutees plus tard dans la config : pas besoin de
     * dupliquer ce loot dans chaque famille a la main.
     */
    private void loadSharedGoodEffects() {
        sharedGoodEffects.clear();
        for (Map<?, ?> raw : luckyBlocksConfig.get().getMapList("effets-communs-bons")) {
            LuckyBlockEffect effect = parseEffect(raw);
            if (effect != null) {
                sharedGoodEffects.add(effect);
            }
        }
    }

    private void loadOreBonuses() {
        oreBonuses.clear();
        bonusMax = luckyBlocksConfig.get().getDouble("bonus-minerais-max", 45.0);
        pityThreshold = Math.max(0, luckyBlocksConfig.get().getInt("pity-seuil", 0));

        ConfigurationSection joinKitSection = luckyBlocksConfig.get().getConfigurationSection("kit-connexion");
        joinKitEnabled = joinKitSection != null && joinKitSection.getBoolean("actif", false);
        joinKitQuantity = joinKitSection != null ? Math.max(0, joinKitSection.getInt("quantite", 0)) : 0;

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

        // Commence par le pool d'effets BONS commun a toutes les familles (voir loadSharedGoodEffects),
        // puis ajoute les effets propres a cette famille (bons ET mauvais).
        List<LuckyBlockEffect> effects = new ArrayList<>(sharedGoodEffects);
        for (Map<?, ?> raw : section.getMapList("effets")) {
            LuckyBlockEffect effect = parseEffect(raw);
            if (effect != null) {
                effects.add(effect);
            }
        }

        // Famille saisonniere optionnelle (Halloween, Noel...) : actif-du/actif-au (format "MM-jj").
        // Absent des deux cotes = famille permanente (comportement par defaut, inchange).
        String actifDu = section.contains("actif-du") ? section.getString("actif-du") : null;
        String actifAu = section.contains("actif-au") ? section.getString("actif-au") : null;

        return new LuckyBlockFamily(id, displayName, material, order, buyPrice, baseGoodChance, recipe, effects,
                actifDu, actifAu);
    }

    /** Meme limitation que sur les Map issues de getMapList() : getOrDefault(k, "texte") ne compile pas sur une
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
            boolean pity = raw.containsKey("pity") && Boolean.parseBoolean(String.valueOf(raw.get("pity")));
            return new LuckyBlockEffect(EffectKind.BON, chance, reward.displayName(), reward, null, null, 0, 0, pity);
        }

        // Un effet MAUVAIS ne peut jamais compter pour le pity (le pity garantit un GROS lot, pas
        // un malus).
        BadEffectType badType = BadEffectType.fromString(getOrDefault(raw, "action", "TNT"));
        return switch (badType) {
            case TNT -> {
                int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
                yield new LuckyBlockEffect(EffectKind.MAUVAIS, chance, "TNT x" + amount, null, badType, null, amount, 0, false);
            }
            case MOBS -> {
                String mob = getOrDefault(raw, "mob", "ZOMBIE");
                int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
                yield new LuckyBlockEffect(EffectKind.MAUVAIS, chance, amount + "x " + mob, null, badType, mob, amount, 0, false);
            }
            case POTION -> {
                String potionType = getOrDefault(raw, "effet-potion", "POISON");
                long durationSeconds = raw.containsKey("duree-secondes") ? Long.parseLong(String.valueOf(raw.get("duree-secondes"))) : 5L;
                int amplifier = raw.containsKey("amplificateur") ? Integer.parseInt(String.valueOf(raw.get("amplificateur"))) : 0;
                yield new LuckyBlockEffect(EffectKind.MAUVAIS, chance, potionType, null, badType, potionType,
                        (int) (durationSeconds * 20), amplifier, false);
            }
            case FOUDRE -> new LuckyBlockEffect(EffectKind.MAUVAIS, chance, "Foudre", null, badType, null, 0, 0, false);
        };
    }

    /** Convertit une Map issue de getMapList (loot LuckyBlock) en ConfigurationSection pour reutiliser RewardParser. */
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

    /** Familles a afficher dans le GUI/shop : toutes les permanentes + les saisonnieres
     * ACTUELLEMENT dans leur periode (voir LuckyBlockFamily#isActiveNow). Une famille saisonniere
     * hors periode reste crafte/donnee/cassable normalement (voir getFamily), elle disparait juste
     * de la vitrine tant que sa saison n'est pas active. */
    public List<LuckyBlockFamily> getFamiliesSorted() {
        List<LuckyBlockFamily> sorted = new ArrayList<>();
        for (LuckyBlockFamily family : families.values()) {
            if (family.isActiveNow()) {
                sorted.add(family);
            }
        }
        sorted.sort(Comparator.comparingInt(LuckyBlockFamily::order));
        return sorted;
    }

    /** Toutes les familles, MEME hors-saison, triees par ordre : utilise par l'editeur en jeu
     * (voir /luckyblockadmin editeur) qui doit pouvoir gerer une famille saisonniere a tout moment. */
    public List<LuckyBlockFamily> getAllFamilies() {
        List<LuckyBlockFamily> all = new ArrayList<>(families.values());
        all.sort(Comparator.comparingInt(LuckyBlockFamily::order));
        return all;
    }

    public LuckyBlockFamily getFamily(String id) {
        return id == null ? null : families.get(id.toLowerCase());
    }

    /**
     * Tirage en 2 etapes : d'abord BON ou MAUVAIS selon la chance de base de la famille
     * (+ bonus de minerais places a cote du bloc, plafonne), puis tirage pondere d'un effet
     * precis au sein du pool correspondant (BON ou MAUVAIS).
     *
     * Systeme de pity (voir pity-seuil dans luckyblocks.yml) : si ce joueur a casse cette famille
     * pity-seuil fois de suite sans obtenir d'effet "pity" (gros lot, voir LuckyBlockEffect#pity),
     * le tirage est force sur le pool des effets pity de cette famille (garanti). Des qu'un effet
     * pity est obtenu (force ou naturellement), le compteur repart a 0.
     */
    public LuckyBlockEffect pickEffect(UUID playerId, LuckyBlockFamily family, double bonusPercent) {
        if (family.effects().isEmpty()) {
            return null;
        }

        if (pityThreshold > 0 && getPityProgress(playerId, family.id()) >= pityThreshold) {
            List<LuckyBlockEffect> pityPool = family.effects().stream().filter(LuckyBlockEffect::pity).toList();
            if (!pityPool.isEmpty()) {
                resetPity(playerId, family.id());
                return pickWeighted(pityPool);
            }
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
        LuckyBlockEffect effect = pickWeighted(pool);
        if (pityThreshold > 0) {
            if (effect.pity()) {
                resetPity(playerId, family.id());
            } else {
                incrementPity(playerId, family.id());
            }
        }
        return effect;
    }

    // ---- Pity (voir pickEffect) ----

    public int getPityThreshold() {
        return pityThreshold;
    }

    // ---- Kit de connexion (voir luckyblocks.yml: kit-connexion) ----

    public boolean isJoinKitEnabled() {
        return joinKitEnabled;
    }

    public int getJoinKitQuantity() {
        return joinKitQuantity;
    }

    /** Casses consecutives de ce joueur sur cette famille sans effet "pity" (0 si aucune ou systeme desactive). */
    public int getPityProgress(UUID playerId, String familyId) {
        Map<String, Integer> perFamily = pityCounters.get(playerId);
        return perFamily == null ? 0 : perFamily.getOrDefault(familyId, 0);
    }

    private void incrementPity(UUID playerId, String familyId) {
        pityCounters.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .merge(familyId, 1, Integer::sum);
    }

    private void resetPity(UUID playerId, String familyId) {
        Map<String, Integer> perFamily = pityCounters.get(playerId);
        if (perFamily != null) {
            perFamily.put(familyId, 0);
        }
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
                .lore(List.of("&7Clic-droit en main", "&7pour declencher un effet aleatoire !"))
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

    /** (Re)enregistre les recettes de craft de toutes les familles ACTUELLEMENT actives (une
     * famille saisonniere hors periode n'est pas craftable, seulement obtenable via
     * /luckyblockadmin give ou en recompense). Appele au demarrage et a /luckyblockadmin reload :
     * pour qu'une famille saisonniere redevienne craftable/non-craftable exactement au jour dit
     * sans redemarrer le serveur, relancez simplement /luckyblockadmin reload ce jour-la. */
    public void registerRecipes() {
        unregisterRecipes();
        for (LuckyBlockFamily family : families.values()) {
            if (family.recipe().isEmpty() || !family.isActiveNow()) {
                continue;
            }
            try {
                NamespacedKey recipeKey = recipeKey(family.id());
                ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, createItem(family));
                for (RecipeIngredient ingredient : family.recipe()) {
                    recipe.addIngredient(ingredient.amount(), ingredient.material());
                }
                Bukkit.addRecipe(recipe);
            } catch (IllegalArgumentException e) {
                // Ex: total des quantites > 9 (limite Bukkit pour une recette sans forme).
                // Une recette invalide dans luckyblocks.yml ne doit jamais empecher le plugin
                // entier de demarrer : on l'ignore simplement (le Lucky Block reste obtenable
                // via /luckyblockadmin give ou en recompense).
                plugin.getLogger().severe("Recette invalide pour le Lucky Block '" + family.id() + "' : " + e.getMessage());
            }
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

    // ---- Editeur de familles/effets en jeu (voir /luckyblockadmin editeur) ----

    private ConfigurationSection getFamilySection(String id) {
        ConfigurationSection root = luckyBlocksConfig.get().getConfigurationSection("familles");
        return root == null ? null : root.getConfigurationSection(id.toLowerCase());
    }

    private void editFamilySection(String id, java.util.function.Consumer<ConfigurationSection> editor) {
        ConfigurationSection section = getFamilySection(id);
        if (section == null) {
            return;
        }
        editor.accept(section);
        luckyBlocksConfig.save();
        loadFamilies();
    }

    /** Cree une nouvelle famille (prix/chance par defaut, sans effet propre : elle herite quand
     * meme du pool commun, voir loadSharedGoodEffects) et la sauvegarde immediatement. */
    public synchronized void addFamily(String id, String nom, Material materiel) {
        ConfigurationSection root = luckyBlocksConfig.get().getConfigurationSection("familles");
        if (root == null) {
            root = luckyBlocksConfig.get().createSection("familles");
        }
        ConfigurationSection section = root.createSection(id.toLowerCase());
        section.set("nom", nom);
        section.set("materiel", materiel.name());
        section.set("prix-achat", 0);
        section.set("chance-bonne-base", 50.0);
        luckyBlocksConfig.save();
        loadFamilies();
    }

    public synchronized void removeFamily(String id) {
        ConfigurationSection root = luckyBlocksConfig.get().getConfigurationSection("familles");
        if (root != null) {
            root.set(id.toLowerCase(), null);
            luckyBlocksConfig.save();
            loadFamilies();
        }
    }

    public synchronized void setFamillePrixAchat(String id, double prix) {
        editFamilySection(id, section -> section.set("prix-achat", Math.max(0, prix)));
    }

    public synchronized void setFamilleChanceBonne(String id, double chance) {
        editFamilySection(id, section -> section.set("chance-bonne-base", Math.max(0, Math.min(100, chance))));
    }

    /** "MM-jj" ou null pour effacer (voir "familles saisonnieres"). */
    public synchronized void setFamilleActifDu(String id, String valeur) {
        editFamilySection(id, section -> section.set("actif-du", valeur));
    }

    public synchronized void setFamilleActifAu(String id, String valeur) {
        editFamilySection(id, section -> section.set("actif-au", valeur));
    }

    /** Copie generique d'une Map issue de getMapList() (type capture inconnu) vers une Map modifiable. */
    private static Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    /** Les effets PROPRES a cette famille (hors pool commun, voir "effets" dans luckyblocks.yml),
     * dans l'ordre de la config : ce que l'editeur en jeu liste/modifie/supprime par index. */
    public synchronized List<LuckyBlockEffect> getOwnEffects(String familyId) {
        ConfigurationSection section = getFamilySection(familyId);
        if (section == null) {
            return List.of();
        }
        List<LuckyBlockEffect> result = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("effets")) {
            LuckyBlockEffect effect = parseEffect(raw);
            if (effect != null) {
                result.add(effect);
            }
        }
        return result;
    }

    /** Ajoute un effet BON de type ITEM a partir d'un ItemStack (typiquement l'objet tenu en main). */
    public synchronized void addGoodEffectFromItem(String familyId, ItemStack modele, double chance) {
        ConfigurationSection section = getFamilySection(familyId);
        if (section == null) {
            return;
        }
        List<Map<?, ?>> effets = new ArrayList<>(section.getMapList("effets"));
        Map<String, Object> recompense = new LinkedHashMap<>();
        recompense.put("type", "ITEM");
        recompense.put("materiel", modele.getType().name());
        recompense.put("quantite", Math.max(1, modele.getAmount()));
        Map<String, Object> effet = new LinkedHashMap<>();
        effet.put("type", "BON");
        effet.put("chance", chance);
        effet.put("recompense", recompense);
        effets.add(effet);
        section.set("effets", effets);
        luckyBlocksConfig.save();
        loadFamilies();
    }

    /** Ajoute un effet BON de type COMMANDE (execute une commande console, {joueur} remplace par
     * le nom du joueur qui obtient l'effet). */
    public synchronized void addGoodEffectFromCommand(String familyId, String commande, double chance) {
        ConfigurationSection section = getFamilySection(familyId);
        if (section == null) {
            return;
        }
        List<Map<?, ?>> effets = new ArrayList<>(section.getMapList("effets"));
        Map<String, Object> recompense = new LinkedHashMap<>();
        recompense.put("type", "COMMANDE");
        recompense.put("commande", commande);
        recompense.put("nom", commande);
        Map<String, Object> effet = new LinkedHashMap<>();
        effet.put("type", "BON");
        effet.put("chance", chance);
        effet.put("recompense", recompense);
        effets.add(effet);
        section.set("effets", effets);
        luckyBlocksConfig.save();
        loadFamilies();
    }

    /** Ajoute un effet MAUVAIS avec des valeurs par defaut pour son type (modifiables ensuite dans
     * luckyblocks.yml si besoin ; seule la chance est editable depuis l'editeur en jeu). */
    public synchronized void addBadEffect(String familyId, BadEffectType type, double chance) {
        ConfigurationSection section = getFamilySection(familyId);
        if (section == null) {
            return;
        }
        List<Map<?, ?>> effets = new ArrayList<>(section.getMapList("effets"));
        Map<String, Object> effet = new LinkedHashMap<>();
        effet.put("type", "MAUVAIS");
        effet.put("chance", chance);
        effet.put("action", type.name());
        switch (type) {
            case TNT -> effet.put("quantite", 3);
            case MOBS -> {
                effet.put("mob", "ZOMBIE");
                effet.put("quantite", 3);
            }
            case POTION -> {
                effet.put("effet-potion", "POISON");
                effet.put("duree-secondes", 5);
                effet.put("amplificateur", 0);
            }
            case FOUDRE -> {
            }
        }
        effets.add(effet);
        section.set("effets", effets);
        luckyBlocksConfig.save();
        loadFamilies();
    }

    public synchronized void setEffectChanceAt(String familyId, int index, double chance) {
        ConfigurationSection section = getFamilySection(familyId);
        if (section == null) {
            return;
        }
        List<Map<?, ?>> effets = new ArrayList<>(section.getMapList("effets"));
        if (index < 0 || index >= effets.size()) {
            return;
        }
        Map<String, Object> effet = copyMap(effets.get(index));
        effet.put("chance", Math.max(0, chance));
        effets.set(index, effet);
        section.set("effets", effets);
        luckyBlocksConfig.save();
        loadFamilies();
    }

    public synchronized void removeEffectAt(String familyId, int index) {
        ConfigurationSection section = getFamilySection(familyId);
        if (section == null) {
            return;
        }
        List<Map<?, ?>> effets = new ArrayList<>(section.getMapList("effets"));
        if (index < 0 || index >= effets.size()) {
            return;
        }
        effets.remove(index);
        section.set("effets", effets);
        luckyBlocksConfig.save();
        loadFamilies();
    }

}
