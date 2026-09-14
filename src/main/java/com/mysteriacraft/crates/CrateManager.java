package com.mysteriacraft.crates;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardType;
import com.mysteriacraft.core.storage.Database;
import com.mysteriacraft.customitems.CustomItemDefinition;
import com.mysteriacraft.customitems.CustomItemManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Charge les caisses depuis crates.yml et gere les cles virtuelles (persistees en SQLite,
 * aucun objet physique requis) ainsi que le tirage pondere des recompenses.
 */
public class CrateManager {

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager cratesConfig;

    private final Map<String, Crate> crates = new LinkedHashMap<>();

    /** Branche apres coup via setCustomItemManager() : le module Custom Items est initialise
     * APRES Crates (voir MysteriaCraft#onEnable), donc indisponible au moment du premier chargement. */
    private CustomItemManager customItemManager;

    public CrateManager(Plugin plugin, Database database, ConfigManager cratesConfig) {
        this.plugin = plugin;
        this.database = database;
        this.cratesConfig = cratesConfig;
        createTables();
        loadCrates();
    }

    private void createTables() {
        String keysTable = "CREATE TABLE IF NOT EXISTS crate_keys (" +
                "uuid TEXT NOT NULL, " +
                "crate_id TEXT NOT NULL, " +
                "quantite INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, crate_id)" +
                ");";
        String pityTable = "CREATE TABLE IF NOT EXISTS crate_pity (" +
                "uuid TEXT NOT NULL, " +
                "crate_id TEXT NOT NULL, " +
                "compteur INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, crate_id)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(keysTable)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'crate_keys' : " + e.getMessage());
        }
        try (PreparedStatement statement = connection.prepareStatement(pityTable)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'crate_pity' : " + e.getMessage());
        }
    }

    public void loadCrates() {
        crates.clear();
        ConfigurationSection root = cratesConfig.get().getConfigurationSection("crates");
        if (root == null) {
            plugin.getLogger().warning("Aucune caisse trouvee dans crates.yml (section 'crates' manquante).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                Crate crate = parseCrate(id, section);
                crates.put(id.toLowerCase(), crate);
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors du chargement de la caisse '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(crates.size() + " caisse(s) chargee(s) depuis crates.yml.");
        syncAutoCustomItemRewards();
    }

    /**
     * Branche le module Custom Items (indisponible a la construction de CrateManager, voir le
     * commentaire du champ) et effectue immediatement une premiere synchronisation.
     */
    public void setCustomItemManager(CustomItemManager customItemManager) {
        this.customItemManager = customItemManager;
        syncAutoCustomItemRewards();
    }

    /**
     * Ajoute automatiquement au tirage de CHAQUE caisse chargee tout item custom marque
     * "caisse-auto" (voir custom_items.yml) qui n'y figure pas deja explicitement (identifie par
     * son item-id, pour ne jamais dupliquer un ajout manuel). Appele a chaque (re)chargement de
     * crates.yml et a chaque fois que le module Custom Items est (re)charge : une nouvelle caisse
     * comme un nouvel item custom sont donc pris en compte sans aucune manipulation de crates.yml.
     * Idempotent : rappeler cette methode plusieurs fois ne cree jamais de doublon.
     */
    private void syncAutoCustomItemRewards() {
        if (customItemManager == null) {
            return;
        }
        List<CustomItemDefinition> autoItems = customItemManager.getItemsSorted().stream()
                .filter(CustomItemDefinition::crateAuto)
                .toList();
        if (autoItems.isEmpty()) {
            return;
        }

        for (Crate crate : crates.values()) {
            Set<String> existingCustomItemIds = new HashSet<>();
            for (CrateReward reward : crate.rewards()) {
                if (reward.type() == RewardType.OBJET_CUSTOM) {
                    existingCustomItemIds.add(reward.reward().customItemId().toLowerCase());
                }
            }

            for (CustomItemDefinition definition : autoItems) {
                if (existingCustomItemIds.contains(definition.id().toLowerCase())) {
                    continue;
                }
                Rarity rarity = Rarity.fromString(definition.crateRarity());
                ItemStack icon = customItemManager.createItem(definition, definition.crateQuantity());
                Reward reward = Reward.ofCustomItem(definition.id(), definition.crateQuantity(),
                        definition.displayName(), icon);
                crate.rewards().add(new CrateReward(definition.id() + "-auto", reward, definition.crateChance(), rarity));
            }
        }
    }

    private Crate parseCrate(String id, ConfigurationSection section) {
        String displayName = section.getString("nom", id);
        Material icon = Material.matchMaterial(section.getString("icone", "CHEST"));
        if (icon == null) {
            icon = Material.CHEST;
        }
        int order = section.getInt("ordre", 0);
        List<String> lore = section.getStringList("lore");
        String permission = section.getString("permission", "");
        CrateAnimationType animation = CrateAnimationType.fromString(section.getString("animation", "INSTANT"));
        int drawsPerOpen = Math.max(1, section.getInt("tirages", 1));
        double keyPrice = section.getDouble("prix-cle", 0);
        int pityThreshold = Math.max(0, section.getInt("pity-seuil", 0));

        List<CrateReward> rewards = new ArrayList<>();
        List<Map<?, ?>> lootMaps = section.getMapList("loot");
        for (Map<?, ?> raw : lootMaps) {
            CrateReward reward = parseReward(raw);
            if (reward != null) {
                rewards.add(reward);
            }
        }

        return new Crate(id, displayName, icon, order, lore, permission, animation,
                drawsPerOpen, keyPrice, pityThreshold, rewards);
    }

    /** section.getMapList()/raw.get() renvoient des Map&lt;?, ?&gt; : getOrDefault(k, "texte") ne
     * compile pas dessus (le compilateur ne peut pas prouver qu'un String correspond au type
     * capture de la valeur). On relit donc la valeur brute et on gere le defaut nous-memes. */
    private static String getOrDefault(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : fallback;
    }

    private CrateReward parseReward(Map<?, ?> raw) {
        String id = getOrDefault(raw, "id", "recompense");
        String typeRaw = raw.containsKey("type") ? String.valueOf(raw.get("type")).toUpperCase() : "ITEM";
        double chance = raw.containsKey("chance") ? Double.parseDouble(String.valueOf(raw.get("chance"))) : 1.0;
        Rarity rarity = Rarity.fromString(raw.containsKey("rarete") ? String.valueOf(raw.get("rarete")) : null);

        if (typeRaw.equals("ECONOMIE")) {
            double amount = raw.containsKey("montant") ? Double.parseDouble(String.valueOf(raw.get("montant"))) : 0;
            Material iconMaterial = Material.matchMaterial(getOrDefault(raw, "icone", "GOLD_INGOT"));
            if (iconMaterial == null) {
                iconMaterial = Material.GOLD_INGOT;
            }
            String displayName = raw.containsKey("nom") ? String.valueOf(raw.get("nom"))
                    : (rarity.color() + "" + amount + "$");
            ItemStack displayIcon = new ItemBuilder(iconMaterial)
                    .name(displayName)
                    .lore(List.of("&7Recompense : &e" + amount + "$"))
                    .build();
            Reward reward = Reward.ofEconomy(amount, displayName, displayIcon);
            return new CrateReward(id, reward, chance, rarity);
        }

        if (typeRaw.equals("CLE_CAISSE")) {
            String crateId = getOrDefault(raw, "caisse", "");
            int keyAmount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
            String displayName = raw.containsKey("nom") ? String.valueOf(raw.get("nom"))
                    : rarity.color() + "" + keyAmount + " cle(s) - " + crateId;
            Material iconMaterial = Material.matchMaterial(getOrDefault(raw, "icone", "TRIPWIRE_HOOK"));
            if (iconMaterial == null) {
                iconMaterial = Material.TRIPWIRE_HOOK;
            }
            ItemStack displayIcon = new ItemBuilder(iconMaterial, keyAmount).name(displayName).build();
            Reward reward = Reward.ofCrateKey(crateId, keyAmount, displayName, displayIcon);
            return new CrateReward(id, reward, chance, rarity);
        }

        if (typeRaw.equals("PET")) {
            String petId = getOrDefault(raw, "pet-id", "");
            String displayName = raw.containsKey("nom") ? String.valueOf(raw.get("nom")) : rarity.color() + "Pet : " + petId;
            Material iconMaterial = Material.matchMaterial(getOrDefault(raw, "icone", "BONE"));
            if (iconMaterial == null) {
                iconMaterial = Material.BONE;
            }
            ItemStack displayIcon = new ItemBuilder(iconMaterial).name(displayName).build();
            Reward reward = Reward.ofPet(petId, displayName, displayIcon);
            return new CrateReward(id, reward, chance, rarity);
        }

        if (typeRaw.equals("LUCKYBLOCK")) {
            String familyId = getOrDefault(raw, "famille", "");
            String displayName = raw.containsKey("nom") ? String.valueOf(raw.get("nom")) : rarity.color() + "Lucky Block : " + familyId;
            Material iconMaterial = Material.matchMaterial(getOrDefault(raw, "icone", "GOLD_BLOCK"));
            if (iconMaterial == null) {
                iconMaterial = Material.GOLD_BLOCK;
            }
            ItemStack displayIcon = new ItemBuilder(iconMaterial).name(displayName).build();
            Reward reward = Reward.ofLuckyBlock(familyId, displayName, displayIcon);
            return new CrateReward(id, reward, chance, rarity);
        }

        if (typeRaw.equals("OBJET_CUSTOM")) {
            String customItemId = getOrDefault(raw, "item-id", "");
            int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
            String displayName = raw.containsKey("nom") ? String.valueOf(raw.get("nom")) : rarity.color() + "Objet custom : " + customItemId;
            Material iconMaterial = Material.matchMaterial(getOrDefault(raw, "icone", "IRON_INGOT"));
            if (iconMaterial == null) {
                iconMaterial = Material.IRON_INGOT;
            }
            ItemStack displayIcon = new ItemBuilder(iconMaterial, amount).name(displayName).build();
            Reward reward = Reward.ofCustomItem(customItemId, amount, displayName, displayIcon);
            return new CrateReward(id, reward, chance, rarity);
        }

        if (typeRaw.equals("GENERATEUR")) {
            String generatorTypeId = getOrDefault(raw, "type-id", "");
            int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
            String displayName = raw.containsKey("nom") ? String.valueOf(raw.get("nom")) : rarity.color() + "Generateur : " + generatorTypeId;
            Material iconMaterial = Material.matchMaterial(getOrDefault(raw, "icone", "IRON_ORE"));
            if (iconMaterial == null) {
                iconMaterial = Material.IRON_ORE;
            }
            ItemStack displayIcon = new ItemBuilder(iconMaterial, amount).name(displayName).build();
            Reward reward = Reward.ofGenerator(generatorTypeId, amount, displayName, displayIcon);
            return new CrateReward(id, reward, chance, rarity);
        }

        if (typeRaw.equals("BOOST_XP")) {
            long durationSeconds = raw.containsKey("duree-secondes") ? Long.parseLong(String.valueOf(raw.get("duree-secondes"))) : 600L;
            double multiplier = raw.containsKey("multiplicateur") ? Double.parseDouble(String.valueOf(raw.get("multiplicateur"))) : 2.0;
            String displayName = raw.containsKey("nom") ? String.valueOf(raw.get("nom")) : rarity.color() + "Boost XP x" + multiplier;
            Material iconMaterial = Material.matchMaterial(getOrDefault(raw, "icone", "NETHER_STAR"));
            if (iconMaterial == null) {
                iconMaterial = Material.NETHER_STAR;
            }
            ItemStack displayIcon = new ItemBuilder(iconMaterial).name(displayName).build();
            Reward reward = Reward.ofXpBooster(durationSeconds, multiplier, displayName, displayIcon);
            return new CrateReward(id, reward, chance, rarity);
        }

        Object materialRaw = raw.get("materiel");
        if (materialRaw == null) {
            plugin.getLogger().warning("Recompense sans 'materiel' ignoree dans crates.yml (id=" + id + ").");
            return null;
        }
        Material material = Material.matchMaterial(String.valueOf(materialRaw));
        if (material == null) {
            plugin.getLogger().warning("Materiau inconnu dans crates.yml : " + materialRaw);
            return null;
        }
        int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
        ItemStack item = new ItemStack(material, Math.max(1, amount));

        String displayName = raw.containsKey("nom") ? String.valueOf(raw.get("nom"))
                : rarity.color() + item.getType().name().replace('_', ' ');
        ItemStack displayIcon = new ItemBuilder(material, Math.max(1, amount))
                .name(displayName)
                .build();

        Reward reward = Reward.ofItem(item, displayName, displayIcon);
        return new CrateReward(id, reward, chance, rarity);
    }

    public List<Crate> getCratesSorted() {
        List<Crate> sorted = new ArrayList<>(crates.values());
        sorted.sort(Comparator.comparingInt(Crate::order));
        return sorted;
    }

    public Crate getCrate(String id) {
        return crates.get(id.toLowerCase());
    }

    /** Tirage pondere d'une recompense parmi celles de la caisse, selon leur "chance" relative. */
    public CrateReward pickReward(Crate crate) {
        return pickReward(crate.rewards());
    }

    private CrateReward pickReward(List<CrateReward> pool) {
        double totalWeight = 0;
        for (CrateReward reward : pool) {
            totalWeight += reward.chance();
        }
        if (totalWeight <= 0 || pool.isEmpty()) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (CrateReward reward : pool) {
            cumulative += reward.chance();
            if (roll < cumulative) {
                return reward;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** Tirage pondere d'une recompense de rarete LEGENDAIRE uniquement (utilise pour la garantie "pity"). */
    public CrateReward pickLegendary(Crate crate) {
        List<CrateReward> legendaries = crate.rewards().stream()
                .filter(reward -> reward.rarity() == Rarity.LEGENDAIRE)
                .toList();
        return legendaries.isEmpty() ? pickReward(crate) : pickReward(legendaries);
    }

    /**
     * Tire drawsPerOpen recompenses pour une ouverture. Si forceLegendary est vrai, la premiere
     * recompense tiree est garantie LEGENDAIRE (systeme "pity").
     */
    public List<CrateReward> pickRewards(Crate crate, boolean forceLegendary) {
        List<CrateReward> results = new ArrayList<>();
        for (int i = 0; i < crate.drawsPerOpen(); i++) {
            CrateReward reward = (i == 0 && forceLegendary) ? pickLegendary(crate) : pickReward(crate);
            if (reward != null) {
                results.add(reward);
            }
        }
        return results;
    }

    // ---- Cles virtuelles ----

    public synchronized int getKeyCount(UUID uuid, String crateId) {
        String select = "SELECT quantite FROM crate_keys WHERE uuid = ? AND crate_id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, crateId.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("quantite");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture cles caisse '" + crateId + "' pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    /** Ajoute (ou retire si negatif) des cles virtuelles, jamais sous 0. Renvoie la nouvelle quantite. */
    public synchronized int addKeys(UUID uuid, String crateId, int delta) {
        int current = getKeyCount(uuid, crateId);
        int updated = Math.max(0, current + delta);

        String upsert = "INSERT INTO crate_keys (uuid, crate_id, quantite) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, crate_id) DO UPDATE SET quantite = excluded.quantite;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, crateId.toLowerCase());
            statement.setInt(3, updated);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour cles caisse '" + crateId + "' pour " + uuid + " : " + e.getMessage());
        }
        return updated;
    }

    /** Consomme une cle si le joueur en possede au moins une. Renvoie true si consommee. */
    public synchronized boolean consumeKey(UUID uuid, String crateId) {
        int current = getKeyCount(uuid, crateId);
        if (current <= 0) {
            return false;
        }
        addKeys(uuid, crateId, -1);
        return true;
    }

    public synchronized int setKeys(UUID uuid, String crateId, int amount) {
        return addKeys(uuid, crateId, amount - getKeyCount(uuid, crateId));
    }

    // ---- Systeme "pity" (garantie de legendaire) ----

    public synchronized int getPityCount(UUID uuid, String crateId) {
        String select = "SELECT compteur FROM crate_pity WHERE uuid = ? AND crate_id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, crateId.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("compteur");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture pity caisse '" + crateId + "' pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    private synchronized void setPityCount(UUID uuid, String crateId, int value) {
        String upsert = "INSERT INTO crate_pity (uuid, crate_id, compteur) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, crate_id) DO UPDATE SET compteur = excluded.compteur;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, crateId.toLowerCase());
            statement.setInt(3, Math.max(0, value));
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour pity caisse '" + crateId + "' pour " + uuid + " : " + e.getMessage());
        }
    }

    public synchronized void incrementPity(UUID uuid, String crateId) {
        setPityCount(uuid, crateId, getPityCount(uuid, crateId) + 1);
    }

    public synchronized void resetPity(UUID uuid, String crateId) {
        setPityCount(uuid, crateId, 0);
    }
}
