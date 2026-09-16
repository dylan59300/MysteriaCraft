package com.mysteriacraft.customitems.machine;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.storage.Database;
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
 * Charge la configuration de la Machine a Transformation (tiers structurels, minerais acceptes
 * -> famille de Lucky Block cible, types de carburant, amelioration) et fabrique/marque son bloc.
 *
 * IMPORTANT : contrairement a un ItemStack ou une Entity, un Block "nu" (ex: IRON_BLOCK) n'a PAS
 * de PersistentDataContainer sur paper-api 1.20.1 (seuls les blocs avec tile entity, comme les
 * coffres, en ont un via leur BlockState). L'etat de chaque machine posee (tier actuel, charges
 * de carburant, cooldown actif, bonus de reussite, hologramme) est donc suivi via une table
 * SQLite dediee indexee par position, mise en cache memoire (write-through : chaque ecriture met
 * a jour le cache puis persiste en base de facon asynchrone).
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
     * chance-luckyblock (probabilite d'obtenir le Lucky Block cible plutot qu'un item custom
     * aleatoire) et son cooldown de base, sa jauge de carburant affichee, et l'id de l'objet
     * custom ("kit") qui permet d'y acceder DEPUIS le tier precedent. kitItemId est null pour le
     * tout premier tier (celui donne par /machine give, aucun kit necessaire).
     */
    public record MachineTier(String id, String displayName, Material block, double chance,
                               long cooldownSeconds, int fuelGaugeMax, String kitItemId) {
    }

    /** Etat persiste d'une machine posee, indexe par position. -1 sur activeCooldownSeconds
     * signifie "jamais ravitaillee/changee de tier depuis sa pose" (utilise le cooldown du tier). */
    private static final class MachineState {
        String tierId;
        int fuel;
        long lastUseMillis;
        long activeCooldownSeconds = -1;
        double bonusReussite;
        UUID hologramUuid;
        /** Nombre de Lucky Blocks CONSECUTIFS obtenus (remis a 0 des qu'un item custom sort a la
         * place), pour le bonus de streak. */
        int streak;
    }

    /** Agregat des statistiques d'un joueur sur toutes ses machines confondues. */
    public record PlayerStats(int totalEssais, int totalReussites, Material minerauFavori, int essaisMinerauFavori) {
        public double tauxReussite() {
            return totalEssais == 0 ? 0.0 : (100.0 * totalReussites / totalEssais);
        }
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager customItemsConfig;
    private final NamespacedKey machineKey;

    /** Machines actuellement posees, indexees par position (chargees au demarrage depuis la base). */
    private final Map<Location, MachineState> machines = new ConcurrentHashMap<>();

    /** LinkedHashMap : l'ordre de declaration dans machine-transformation.tiers fixe la progression
     * (le 1er tier declare est le tier de base, chaque tier suivant necessite le kit du precedent). */
    private final Map<String, MachineTier> tiers = new LinkedHashMap<>();
    private final Map<String, FuelType> fuelTypes = new LinkedHashMap<>();
    private String upgradeItemId = "amelioration_machine";
    private double bonusPerUpgrade = 5.0;
    private double bonusMax = 30.0;
    private boolean autoAlimentation = true;
    private int hologramSegments = 10;
    private int hologramFuelGaugeMaxDefault = 20;
    private boolean hologramCompact = false;
    /** LinkedHashMap : l'ordre de declaration dans machine-transformation.minerais fixe la priorite
     * de traitement de l'auto-alimentation (le 1er minerai present dans le coffre d'entree est traite en premier). */
    private final Map<Material, String> acceptedOres = new LinkedHashMap<>();

    /** Bonus de chance-luckyblock temporaire accumule par Lucky Block consecutif (remis a 0 des
     * qu'un item custom sort a la place). */
    private double streakBonusPerSuccess = 0.0;
    private double streakBonusMax = 0.0;

    /** Boost "carburant illimite" actif par joueur (uuid -> timestamp d'expiration en ms), charge au demarrage. */
    private final Map<UUID, Long> fuelBoosts = new ConcurrentHashMap<>();
    private String fuelBoostItemId = "carburant_illimite";
    private long fuelBoostDurationSeconds = 86400L;

    /** "Double loot" temporaire (evenement admin, /machine doubleloot) : tant qu'actif, TOUTES les
     * machines donnent a la fois le Lucky Block ET un item custom a chaque echange, au lieu de
     * l'un ou l'autre. En memoire uniquement (evenement ponctuel, pas persiste entre redemarrages). */
    private volatile long doubleLootUntilMillis = 0L;

    /** Systeme de pity : nombre d'echanges CONSECUTIFS de ce joueur (toutes machines confondues)
     * sans obtenir de Lucky Block (remis a 0 des qu'il en obtient un, force ou naturel). En
     * memoire uniquement, comme les autres compteurs "depuis le dernier demarrage" du plugin. */
    private final Map<UUID, Integer> pityCounters = new ConcurrentHashMap<>();
    /** Nombre d'echanges sans Lucky Block avant garantie (0 = systeme de pity desactive). */
    private int pityThreshold = 0;

    /** Poids de tirage du loot ALEATOIRE de la machine (voir machine-transformation.poids-loot dans
     * custom_items.yml) : plus le poids est eleve, plus une entree (item custom/generateur/machine)
     * a de chances de sortir. Toute entree absente de ces maps recoit lootWeightDefault. */
    private final Map<String, Integer> lootWeightItems = new LinkedHashMap<>();
    private final Map<String, Integer> lootWeightGenerators = new LinkedHashMap<>();
    private final Map<String, Integer> lootWeightGeneratorsLuckyBlock = new LinkedHashMap<>();
    private final Map<String, Integer> lootWeightMachines = new LinkedHashMap<>();
    private int lootWeightDefault = 10;

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    public MachineManager(Plugin plugin, Database database, ConfigManager customItemsConfig) {
        this.plugin = plugin;
        this.database = database;
        this.customItemsConfig = customItemsConfig;
        this.machineKey = new NamespacedKey(plugin, "machine-transformation");
        createTable();
        createFuelBoostTable();
        createStatsTable();
        loadMachines();
        loadFuelBoosts();
        loadConfig();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS machines (" +
                "monde TEXT NOT NULL, " +
                "x INTEGER NOT NULL, " +
                "y INTEGER NOT NULL, " +
                "z INTEGER NOT NULL, " +
                "tier_id TEXT NOT NULL, " +
                "carburant INTEGER NOT NULL DEFAULT 0, " +
                "derniere_utilisation INTEGER NOT NULL DEFAULT 0, " +
                "cooldown_actif INTEGER NOT NULL DEFAULT -1, " +
                "bonus_reussite REAL NOT NULL DEFAULT 0, " +
                "hologramme_uuid TEXT, " +
                "streak INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (monde, x, y, z)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'machines' : " + e.getMessage());
        }
        // Migration : une base creee AVANT l'ajout du streak n'a pas cette colonne (CREATE TABLE IF
        // NOT EXISTS ne la rajoute pas toute seule). Ignore silencieusement si elle existe deja.
        try (PreparedStatement statement = connection.prepareStatement(
                "ALTER TABLE machines ADD COLUMN streak INTEGER NOT NULL DEFAULT 0;")) {
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // Colonne deja presente : rien a faire.
        }
    }

    private void createFuelBoostTable() {
        String sql = "CREATE TABLE IF NOT EXISTS machine_boost_carburant (" +
                "uuid TEXT NOT NULL PRIMARY KEY, " +
                "expiration INTEGER NOT NULL" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'machine_boost_carburant' : " + e.getMessage());
        }
    }

    private void createStatsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS machine_stats (" +
                "uuid TEXT NOT NULL, " +
                "materiau TEXT NOT NULL, " +
                "essais INTEGER NOT NULL DEFAULT 0, " +
                "reussites INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, materiau)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'machine_stats' : " + e.getMessage());
        }
    }

    private void loadMachines() {
        machines.clear();
        String select = "SELECT monde, x, y, z, tier_id, carburant, derniere_utilisation, cooldown_actif, "
                + "bonus_reussite, hologramme_uuid, streak FROM machines;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                World world = Bukkit.getWorld(rs.getString("monde"));
                if (world == null) {
                    continue;
                }
                Location location = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                MachineState state = new MachineState();
                state.tierId = rs.getString("tier_id");
                state.fuel = rs.getInt("carburant");
                state.lastUseMillis = rs.getLong("derniere_utilisation");
                state.activeCooldownSeconds = rs.getLong("cooldown_actif");
                state.bonusReussite = rs.getDouble("bonus_reussite");
                state.streak = rs.getInt("streak");
                String hologramRaw = rs.getString("hologramme_uuid");
                if (hologramRaw != null) {
                    try {
                        state.hologramUuid = UUID.fromString(hologramRaw);
                    } catch (IllegalArgumentException ignored) {
                        // UUID corrompu : l'hologramme sera simplement recree au besoin.
                    }
                }
                machines.put(location, state);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur chargement des machines posees : " + e.getMessage());
        }
        plugin.getLogger().info(machines.size() + " Machine(s) a Transformation rechargee(s) depuis la base.");
    }

    private void persistAsync(Location location, MachineState state) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String upsert = "INSERT INTO machines (monde, x, y, z, tier_id, carburant, derniere_utilisation, "
                    + "cooldown_actif, bonus_reussite, hologramme_uuid, streak) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(monde, x, y, z) DO UPDATE SET tier_id = excluded.tier_id, "
                    + "carburant = excluded.carburant, derniere_utilisation = excluded.derniere_utilisation, "
                    + "cooldown_actif = excluded.cooldown_actif, bonus_reussite = excluded.bonus_reussite, "
                    + "hologramme_uuid = excluded.hologramme_uuid, streak = excluded.streak;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, location.getWorld().getName());
                statement.setInt(2, location.getBlockX());
                statement.setInt(3, location.getBlockY());
                statement.setInt(4, location.getBlockZ());
                statement.setString(5, state.tierId);
                statement.setInt(6, state.fuel);
                statement.setLong(7, state.lastUseMillis);
                statement.setLong(8, state.activeCooldownSeconds);
                statement.setDouble(9, state.bonusReussite);
                statement.setString(10, state.hologramUuid != null ? state.hologramUuid.toString() : null);
                statement.setInt(11, state.streak);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur sauvegarde machine : " + e.getMessage());
            }
        });
    }

    private void deleteAsync(Location location) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String delete = "DELETE FROM machines WHERE monde = ? AND x = ? AND y = ? AND z = ?;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                statement.setString(1, location.getWorld().getName());
                statement.setInt(2, location.getBlockX());
                statement.setInt(3, location.getBlockY());
                statement.setInt(4, location.getBlockZ());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur suppression machine : " + e.getMessage());
            }
        });
    }

    /** Cle de position (bloc entier, sans decimales) utilisee pour indexer machines. */
    private static Location blockKey(Block block) {
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    private void loadFuelBoosts() {
        fuelBoosts.clear();
        String select = "SELECT uuid, expiration FROM machine_boost_carburant;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select);
             ResultSet rs = statement.executeQuery()) {
            long now = System.currentTimeMillis();
            while (rs.next()) {
                long expiration = rs.getLong("expiration");
                if (expiration <= now) {
                    continue;
                }
                try {
                    fuelBoosts.put(UUID.fromString(rs.getString("uuid")), expiration);
                } catch (IllegalArgumentException ignored) {
                    // UUID corrompu : ce boost est simplement ignore.
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur chargement des boosts 'carburant illimite' : " + e.getMessage());
        }
    }

    private void persistFuelBoostAsync(UUID uuid, long expiration) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String upsert = "INSERT INTO machine_boost_carburant (uuid, expiration) VALUES (?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET expiration = excluded.expiration;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, uuid.toString());
                statement.setLong(2, expiration);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur sauvegarde boost 'carburant illimite' pour " + uuid + " : " + e.getMessage());
            }
        });
    }

    /**
     * Active (ou prolonge, en remplacant sa duree restante) le boost "carburant illimite" d'un
     * joueur : tant qu'il est actif, ses transformations manuelles (clic-droit) ne consomment plus
     * de charge de carburant et ne sont jamais bloquees par manque de carburant.
     */
    public void activateFuelBoost(UUID uuid, long durationSeconds) {
        long expiration = System.currentTimeMillis() + durationSeconds * 1000L;
        fuelBoosts.put(uuid, expiration);
        persistFuelBoostAsync(uuid, expiration);
    }

    public String getFuelBoostItemId() {
        return fuelBoostItemId;
    }

    public long getFuelBoostDurationSeconds() {
        return fuelBoostDurationSeconds;
    }

    public boolean hasActiveFuelBoost(UUID uuid) {
        Long expiration = fuelBoosts.get(uuid);
        if (expiration == null) {
            return false;
        }
        if (expiration <= System.currentTimeMillis()) {
            fuelBoosts.remove(uuid);
            return false;
        }
        return true;
    }

    /** Millisecondes restantes du boost "carburant illimite" de ce joueur (0 si inactif). */
    public long getFuelBoostRemainingMillis(UUID uuid) {
        Long expiration = fuelBoosts.get(uuid);
        return expiration == null ? 0L : Math.max(0L, expiration - System.currentTimeMillis());
    }

    // ---- "Double loot" (evenement admin serveur entier) ----

    /** Active (ou prolonge, en remplacant sa duree restante) le "double loot" pour TOUTES les
     * machines du serveur. */
    public void activateDoubleLoot(long durationSeconds) {
        doubleLootUntilMillis = System.currentTimeMillis() + durationSeconds * 1000L;
    }

    public boolean isDoubleLootActive() {
        return System.currentTimeMillis() < doubleLootUntilMillis;
    }

    /** Millisecondes restantes de "double loot" (0 si inactif). */
    public long getDoubleLootRemainingMillis() {
        return Math.max(0L, doubleLootUntilMillis - System.currentTimeMillis());
    }

    // ---- Pity (voir MachineService#attemptTransformation) ----

    public int getPityThreshold() {
        return pityThreshold;
    }

    /** Echanges consecutifs de ce joueur, toutes machines confondues, sans Lucky Block obtenu. */
    public int getPityProgress(UUID playerId) {
        return pityCounters.getOrDefault(playerId, 0);
    }

    /** A appeler apres chaque echange manuel (clic-droit) : incremente si aucun Lucky Block
     * obtenu, remet a 0 sinon. */
    public void recordPityResult(UUID playerId, boolean gotLuckyBlock) {
        if (gotLuckyBlock) {
            pityCounters.remove(playerId);
        } else {
            pityCounters.merge(playerId, 1, Integer::sum);
        }
    }

    // ---- Statistiques joueur (essais/reussites par minerai, pour le menu /machine stats) ----

    /**
     * Enregistre une tentative de transformation manuelle pour ce joueur/minerai. Ecrit en
     * SYNCHRONE (comme QuestService#registerProgress) : evite tout acces concurrent a la
     * Connection SQLite partagee avec les autres ecritures synchrones de cette meme transformation.
     */
    public synchronized void recordAttempt(UUID uuid, Material material, boolean success) {
        String select = "SELECT essais, reussites FROM machine_stats WHERE uuid = ? AND materiau = ?;";
        int essais = 0;
        int reussites = 0;
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, material.name());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    essais = rs.getInt("essais");
                    reussites = rs.getInt("reussites");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture stats machine pour " + uuid + " : " + e.getMessage());
        }

        essais++;
        if (success) {
            reussites++;
        }

        String upsert = "INSERT INTO machine_stats (uuid, materiau, essais, reussites) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(uuid, materiau) DO UPDATE SET essais = excluded.essais, reussites = excluded.reussites;";
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, material.name());
            statement.setInt(3, essais);
            statement.setInt(4, reussites);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur sauvegarde stats machine pour " + uuid + " : " + e.getMessage());
        }
    }

    /** Statistiques agregees (tous minerais confondus) d'un joueur, plus son minerai le plus utilise. */
    public synchronized PlayerStats getPlayerStats(UUID uuid) {
        String select = "SELECT materiau, essais, reussites FROM machine_stats WHERE uuid = ?;";
        int totalEssais = 0;
        int totalReussites = 0;
        Material favori = null;
        int favoriEssais = -1;
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int essais = rs.getInt("essais");
                    totalEssais += essais;
                    totalReussites += rs.getInt("reussites");
                    if (essais > favoriEssais) {
                        favoriEssais = essais;
                        favori = Material.matchMaterial(rs.getString("materiau"));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture stats machine agregees pour " + uuid + " : " + e.getMessage());
        }
        return new PlayerStats(totalEssais, totalReussites, favori, Math.max(0, favoriEssais));
    }

    public void loadConfig() {
        acceptedOres.clear();
        fuelTypes.clear();
        tiers.clear();
        lootWeightItems.clear();
        lootWeightGenerators.clear();
        lootWeightGeneratorsLuckyBlock.clear();
        lootWeightMachines.clear();
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
                double chance = tierSection.getDouble("chance-luckyblock", 20.0);
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

        pityThreshold = Math.max(0, section.getInt("pity-seuil", 0));
        autoAlimentation = section.getBoolean("auto-alimentation", true);
        hologramSegments = Math.max(1, section.getInt("hologramme-segments", 10));
        hologramCompact = section.getBoolean("hologramme-compact", false);

        ConfigurationSection streakSection = section.getConfigurationSection("streak");
        if (streakSection != null) {
            streakBonusPerSuccess = Math.max(0, streakSection.getDouble("bonus-par-succes", 0));
            streakBonusMax = Math.max(0, streakSection.getDouble("bonus-max", 0));
        } else {
            streakBonusPerSuccess = 0.0;
            streakBonusMax = 0.0;
        }

        ConfigurationSection boostSection = section.getConfigurationSection("boost-carburant-illimite");
        if (boostSection != null) {
            fuelBoostItemId = boostSection.getString("item-id", "carburant_illimite").toLowerCase();
            fuelBoostDurationSeconds = Math.max(1, boostSection.getLong("duree-secondes", 86400L));
        }

        ConfigurationSection poidsLoot = section.getConfigurationSection("poids-loot");
        lootWeightDefault = Math.max(1, poidsLoot != null ? poidsLoot.getInt("poids-defaut", 10) : 10);
        if (poidsLoot != null) {
            loadLootWeights(poidsLoot.getConfigurationSection("poids-items"), lootWeightItems);
            loadLootWeights(poidsLoot.getConfigurationSection("poids-generateurs"), lootWeightGenerators);
            loadLootWeights(poidsLoot.getConfigurationSection("poids-generateurs-lb"), lootWeightGeneratorsLuckyBlock);
            loadLootWeights(poidsLoot.getConfigurationSection("poids-machines"), lootWeightMachines);
        }

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

    private void loadLootWeights(ConfigurationSection section, Map<String, Integer> target) {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            target.put(id.toLowerCase(), Math.max(1, section.getInt(id, lootWeightDefault)));
        }
    }

    // ---- Poids de tirage du loot aleatoire (voir machine-transformation.poids-loot) ----

    public int getItemLootWeight(String itemId) {
        return lootWeightItems.getOrDefault(itemId.toLowerCase(), lootWeightDefault);
    }

    public int getGeneratorLootWeight(String generatorTypeId) {
        return lootWeightGenerators.getOrDefault(generatorTypeId.toLowerCase(), lootWeightDefault);
    }

    public int getGeneratorLuckyBlockLootWeight(String familyId) {
        return lootWeightGeneratorsLuckyBlock.getOrDefault(familyId.toLowerCase(), lootWeightDefault);
    }

    public int getMachineLootWeight(String machineId) {
        return lootWeightMachines.getOrDefault(machineId.toLowerCase(), lootWeightDefault);
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

    /** Le tier actuel de ce bloc, ou le tier de base si non defini/invalide/inconnu. */
    public MachineTier getBlockTier(Block block) {
        MachineState state = machines.get(blockKey(block));
        MachineTier tier = state != null ? getTier(state.tierId) : null;
        return tier != null ? tier : getBaseTier();
    }

    /**
     * Fait passer ce bloc au tier donne : change son materiau physique et sa chance/cooldown de
     * base, tout en preservant son etat (carburant, bonus, hologramme). Le cooldown actif est
     * reinitialise a celui du nouveau tier pour que l'amelioration soit immediatement ressentie.
     */
    public void setTier(Block block, MachineTier tier) {
        Location key = blockKey(block);
        MachineState state = machines.computeIfAbsent(key, k -> new MachineState());
        state.tierId = tier.id();
        state.activeCooldownSeconds = tier.cooldownSeconds();

        block.setType(tier.block());
        persistAsync(key, state);
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
                    MessageManager.color("&7pour l'echanger : 1 minerai = 1 loot,"),
                    MessageManager.color("&7toujours une recompense (Lucky Block"),
                    MessageManager.color("&7ou item custom aleatoire)."),
                    MessageManager.color("&7Tier : " + baseTier.displayName()
                            + " &7(&e" + (int) baseTier.chance() + "%&7 de chance Lucky Block)"),
                    MessageManager.color("&7Necessite du carburant pour fonctionner."),
                    MessageManager.color("&7Clic a vide pour voir son etat.")
            ));
            meta.getPersistentDataContainer().set(machineKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Marque un bloc pose comme etant la Machine a Transformation (au tier de base). */
    public void tagBlock(Block block) {
        setTier(block, getBaseTier());
    }

    /** A appeler quand une machine est cassee, pour arreter son suivi (accumulation/particules). */
    public void forgetMachine(Location location) {
        machines.remove(location);
        deleteAsync(location);
    }

    // ---- Nettoyage d'un ancien hologramme (fonctionnalite retiree : plus d'ArmorStand affiche
    // au-dessus des machines) ----

    /** Hologramme associe a cette machine, ou null s'il n'existe pas (jamais cree ou deja retire). */
    public ArmorStand getHologram(Block block) {
        MachineState state = machines.get(blockKey(block));
        if (state == null || state.hologramUuid == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(state.hologramUuid);
        return entity instanceof ArmorStand stand ? stand : null;
    }

    /** Retire l'hologramme de cette machine (a appeler quand le bloc est casse). */
    public void removeHologram(Block block) {
        ArmorStand stand = getHologram(block);
        if (stand != null) {
            stand.remove();
        }
        MachineState state = machines.get(blockKey(block));
        if (state != null) {
            state.hologramUuid = null;
        }
    }

    /** Emplacements de toutes les machines connues (chargees au demarrage + posees depuis). */
    public Set<Location> getActiveMachineLocations() {
        return machines.keySet();
    }

    public boolean isMachineBlock(Block block) {
        return machines.containsKey(blockKey(block));
    }

    public boolean isMachineItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(machineKey, PersistentDataType.BYTE);
    }

    // ---- Etat de la machine (carburant, cooldown, bonus), stocke par position ----

    public int getFuel(Block block) {
        MachineState state = machines.get(blockKey(block));
        return state == null ? 0 : state.fuel;
    }

    public void setFuel(Block block, int amount) {
        Location key = blockKey(block);
        MachineState state = machines.get(key);
        if (state == null) {
            return;
        }
        state.fuel = Math.max(0, amount);
        persistAsync(key, state);
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
        MachineState state = machines.get(blockKey(block));
        if (state == null || state.activeCooldownSeconds < 0) {
            return getBlockTier(block).cooldownSeconds();
        }
        return state.activeCooldownSeconds;
    }

    public void setActiveCooldownSeconds(Block block, long seconds) {
        Location key = blockKey(block);
        MachineState state = machines.get(key);
        if (state == null) {
            return;
        }
        state.activeCooldownSeconds = Math.max(0, seconds);
        persistAsync(key, state);
    }

    public long getLastUseMillis(Block block) {
        MachineState state = machines.get(blockKey(block));
        return state == null ? 0L : state.lastUseMillis;
    }

    public void markUsedNow(Block block) {
        Location key = blockKey(block);
        MachineState state = machines.get(key);
        if (state == null) {
            return;
        }
        state.lastUseMillis = System.currentTimeMillis();
        persistAsync(key, state);
    }

    /** Millisecondes restantes avant la fin du cooldown (0 ou negatif = disponible). */
    public long getRemainingCooldownMillis(Block block) {
        long lastUse = getLastUseMillis(block);
        if (lastUse == 0L) {
            return 0L;
        }
        return (lastUse + getActiveCooldownSeconds(block) * 1000L) - System.currentTimeMillis();
    }

    /** Bonus de chance-luckyblock accumule sur cette machine grace aux ameliorations (0 par defaut). */
    public double getBonusReussite(Block block) {
        MachineState state = machines.get(blockKey(block));
        return state == null ? 0.0 : state.bonusReussite;
    }

    /** Ajoute du bonus de chance-luckyblock (plafonne a bonus-max). Renvoie le nouveau total (0 si
     * ce bloc n'est pas/plus une machine connue). */
    public double addBonusReussite(Block block, double amount) {
        Location key = blockKey(block);
        MachineState state = machines.get(key);
        if (state == null) {
            return 0.0;
        }
        state.bonusReussite = Math.min(bonusMax, state.bonusReussite + amount);
        persistAsync(key, state);
        return state.bonusReussite;
    }

    /** Chance effective (chance du tier + bonus d'amelioration + bonus de streak en cours),
     * plafonnee a 100%, d'obtenir le Lucky Block cible plutot qu'un item custom aleatoire a
     * chaque minerai insere dans cette machine. */
    public double getEffectiveChance(Block block) {
        return Math.min(100.0, getBlockTier(block).chance() + getBonusReussite(block) + getStreakBonus(block));
    }

    // ---- Streak (reussites consecutives) ----

    public int getStreak(Block block) {
        MachineState state = machines.get(blockKey(block));
        return state == null ? 0 : state.streak;
    }

    /** Bonus de chance actuellement accorde par le streak en cours (0 si desactive en config ou streak nul). */
    public double getStreakBonus(Block block) {
        if (streakBonusPerSuccess <= 0) {
            return 0.0;
        }
        return Math.min(streakBonusMax, getStreak(block) * streakBonusPerSuccess);
    }

    /** A appeler apres une transformation reussie : incremente le streak. Renvoie le nouveau streak. */
    public int incrementStreak(Block block) {
        Location key = blockKey(block);
        MachineState state = machines.get(key);
        if (state == null) {
            return 0;
        }
        state.streak++;
        persistAsync(key, state);
        return state.streak;
    }

    /** A appeler quand un item custom sort a la place du Lucky Block : remet le streak a 0. */
    public void resetStreak(Block block) {
        Location key = blockKey(block);
        MachineState state = machines.get(key);
        if (state == null || state.streak == 0) {
            return;
        }
        state.streak = 0;
        persistAsync(key, state);
    }
}
