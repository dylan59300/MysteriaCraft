package com.mysteriacraft.island;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charge/gere le monde dedie aux Iles (skyblock), la position/etat de chaque ile (SQLite,
 * indexee par proprietaire ET par position pour une recherche rapide depuis un evenement de
 * bloc), ses membres de confiance, et les paliers de recompense automatique.
 *
 * IMPORTANT : les iles sont alignees le long de l'axe X, espacees de "espacement-blocs" (voir
 * islands.yml). Etant donne cet espacement fixe et connu, retrouver l'ile (le cas echeant)
 * couvrant un bloc (x, z) donne se fait en O(1) (calcul d'index + lookup dans une Map), sans
 * jamais parcourir la liste de toutes les iles a chaque evenement de bloc/PVP.
 */
public class IslandManager {

    /** Etat persiste d'une ile, indexe par proprietaire ET par index d'allocation (position). */
    public static final class Island {
        UUID owner;
        int index;
        int centerX;
        int centerY;
        int centerZ;
        int size;
        long value;
        int lastTierIndex;
        /** Point d'atterrissage personnalise (/ile sethome), ou null = utiliser le centre par defaut. */
        Double homeX;
        Double homeY;
        Double homeZ;
        float homeYaw;
        float homePitch;
        final Set<UUID> members = ConcurrentHashMap.newKeySet();
        final Set<String> completedChallenges = ConcurrentHashMap.newKeySet();

        public UUID owner() {
            return owner;
        }

        public int size() {
            return size;
        }

        public long value() {
            return value;
        }

        public Set<UUID> members() {
            return members;
        }

        public boolean hasCustomHome() {
            return homeX != null;
        }

        public boolean isTrusted(UUID uuid) {
            return owner.equals(uuid) || members.contains(uuid);
        }
    }

    private record Tier(int level, Reward reward) {
    }

    /** Type de condition d'un defi d'ile (voir "defis" dans islands.yml). */
    public enum ChallengeType {
        NIVEAU, MEMBRES, TAILLE
    }

    /** Un defi d'ile : condition (type + objectif), xp BattlePass et recompense optionnelle,
     * donnes UNE SEULE FOIS des que la condition est remplie. */
    public record Challenge(String id, ChallengeType type, long objective, long xp, Reward reward) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager islandsConfig;

    private World world;
    private String worldName = "iles";
    private int spacing = 300;
    private int startSize = 10;
    private int maxSize = 50;
    private double upgradePrice = 5000;
    private int blocksPerUpgrade = 5;
    private boolean pvpAllowed = false;
    private boolean explosionsAllowed = false;
    private int spawnX = 0;
    private int spawnY = 100;
    private int spawnZ = 0;

    private final Map<Material, Integer> blockValues = new HashMap<>();
    private final List<Tier> tiers = new ArrayList<>();
    private final List<Challenge> challenges = new ArrayList<>();

    /** Iles chargees, indexees par proprietaire (une seule ile par joueur). */
    private final Map<UUID, Island> islandsByOwner = new ConcurrentHashMap<>();
    /** Meme iles, indexees par leur index d'allocation (position) pour la recherche O(1). */
    private final Map<Integer, Island> islandsByIndex = new ConcurrentHashMap<>();

    public IslandManager(Plugin plugin, Database database, ConfigManager islandsConfig) {
        this.plugin = plugin;
        this.database = database;
        this.islandsConfig = islandsConfig;
        createTables();
        loadConfig();
        createWorldIfNeeded();
        loadIslands();
    }

    private void createTables() {
        Connection connection = database.getConnection();
        String iles = "CREATE TABLE IF NOT EXISTS iles (" +
                "uuid_proprietaire TEXT NOT NULL PRIMARY KEY, " +
                "position_index INTEGER NOT NULL UNIQUE, " +
                "centre_x INTEGER NOT NULL, " +
                "centre_y INTEGER NOT NULL, " +
                "centre_z INTEGER NOT NULL, " +
                "taille INTEGER NOT NULL, " +
                "valeur INTEGER NOT NULL DEFAULT 0, " +
                "dernier_palier INTEGER NOT NULL DEFAULT 0, " +
                "home_x REAL, home_y REAL, home_z REAL, home_yaw REAL NOT NULL DEFAULT 0, " +
                "home_pitch REAL NOT NULL DEFAULT 0" +
                ");";
        String membres = "CREATE TABLE IF NOT EXISTS ile_membres (" +
                "uuid_proprietaire TEXT NOT NULL, " +
                "uuid_membre TEXT NOT NULL, " +
                "PRIMARY KEY (uuid_proprietaire, uuid_membre)" +
                ");";
        String compteur = "CREATE TABLE IF NOT EXISTS ile_compteur (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1), " +
                "prochain_index INTEGER NOT NULL DEFAULT 0" +
                ");";
        String defisCompletes = "CREATE TABLE IF NOT EXISTS ile_defis_completes (" +
                "uuid_proprietaire TEXT NOT NULL, " +
                "defi_id TEXT NOT NULL, " +
                "PRIMARY KEY (uuid_proprietaire, defi_id)" +
                ");";
        try (PreparedStatement s1 = connection.prepareStatement(iles);
             PreparedStatement s2 = connection.prepareStatement(membres);
             PreparedStatement s3 = connection.prepareStatement(compteur);
             PreparedStatement s4 = connection.prepareStatement(defisCompletes)) {
            s1.executeUpdate();
            s2.executeUpdate();
            s3.executeUpdate();
            s4.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation tables iles : " + e.getMessage());
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ile_compteur (id, prochain_index) VALUES (1, 0) ON CONFLICT(id) DO NOTHING;")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur initialisation compteur iles : " + e.getMessage());
        }
        // Migration : une base creee AVANT l'ajout de /ile sethome n'a pas ces colonnes (CREATE
        // TABLE IF NOT EXISTS ne les rajoute pas toute seule). Ignore silencieusement si presentes.
        for (String column : new String[] {"home_x REAL", "home_y REAL", "home_z REAL",
                "home_yaw REAL NOT NULL DEFAULT 0", "home_pitch REAL NOT NULL DEFAULT 0"}) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "ALTER TABLE iles ADD COLUMN " + column + ";")) {
                statement.executeUpdate();
            } catch (SQLException ignored) {
                // Colonne deja presente : rien a faire.
            }
        }
    }

    public void loadConfig() {
        worldName = islandsConfig.get().getString("monde", "iles");
        spacing = Math.max(50, islandsConfig.get().getInt("espacement-blocs", 300));
        startSize = Math.max(3, islandsConfig.get().getInt("taille-depart", 10));
        maxSize = Math.max(startSize, islandsConfig.get().getInt("taille-max", 50));
        upgradePrice = Math.max(0, islandsConfig.get().getDouble("prix-agrandissement", 5000));
        blocksPerUpgrade = Math.max(1, islandsConfig.get().getInt("blocs-par-agrandissement", 5));
        pvpAllowed = islandsConfig.get().getBoolean("pvp-autorise", false);
        explosionsAllowed = islandsConfig.get().getBoolean("explosions-autorisees", false);

        ConfigurationSection spawnSection = islandsConfig.get().getConfigurationSection("spawn-monde");
        if (spawnSection != null) {
            spawnX = spawnSection.getInt("x", 0);
            spawnY = spawnSection.getInt("y", 100);
            spawnZ = spawnSection.getInt("z", 0);
        }

        if (spacing < 2 * maxSize + 20) {
            plugin.getLogger().warning("islands.yml : espacement-blocs (" + spacing
                    + ") est trop proche de 2x taille-max, des iles agrandies au maximum pourraient se toucher.");
        }

        blockValues.clear();
        ConfigurationSection valuesSection = islandsConfig.get().getConfigurationSection("valeurs-blocs");
        if (valuesSection != null) {
            for (String materialName : valuesSection.getKeys(false)) {
                Material material = Material.matchMaterial(materialName);
                if (material != null) {
                    blockValues.put(material, valuesSection.getInt(materialName, 0));
                }
            }
        }

        tiers.clear();
        for (Map<?, ?> raw : islandsConfig.get().getMapList("paliers")) {
            Object levelRaw = raw.get("niveau");
            if (levelRaw == null) {
                continue;
            }
            int level = Integer.parseInt(String.valueOf(levelRaw));
            ConfigurationSection rewardSection = mapToSection(raw.get("recompense"));
            Reward reward = RewardParser.parse(rewardSection);
            if (reward != null) {
                tiers.add(new Tier(level, reward));
            }
        }
        tiers.sort((a, b) -> Integer.compare(a.level(), b.level()));

        challenges.clear();
        for (Map<?, ?> raw : islandsConfig.get().getMapList("defis")) {
            String id = raw.containsKey("id") ? String.valueOf(raw.get("id")) : null;
            Object typeRaw = raw.get("type");
            if (id == null || typeRaw == null) {
                continue;
            }
            ChallengeType type;
            try {
                type = ChallengeType.valueOf(String.valueOf(typeRaw).toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Type de defi d'ile inconnu : " + typeRaw);
                continue;
            }
            long objective = raw.containsKey("objectif") ? Long.parseLong(String.valueOf(raw.get("objectif"))) : 0;
            long xp = raw.containsKey("xp") ? Long.parseLong(String.valueOf(raw.get("xp"))) : 0;
            Reward reward = raw.containsKey("recompense") ? RewardParser.parse(mapToSection(raw.get("recompense"))) : null;
            challenges.add(new Challenge(id.toLowerCase(), type, objective, xp, reward));
        }

        plugin.getLogger().info("Iles : " + blockValues.size() + " materiau(x) values, " + tiers.size()
                + " palier(s), " + challenges.size() + " defi(s) charge(s).");
    }

    @SuppressWarnings("unchecked")
    private ConfigurationSection mapToSection(Object rawMap) {
        if (!(rawMap instanceof Map<?, ?> map)) {
            return null;
        }
        org.bukkit.configuration.MemoryConfiguration memoryConfig = new org.bukkit.configuration.MemoryConfiguration();
        ConfigurationSection section = memoryConfig.createSection("recompense");
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            section.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return section;
    }

    private void createWorldIfNeeded() {
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            this.world = existing;
            return;
        }
        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new VoidGenerator());
        creator.environment(World.Environment.NORMAL);
        this.world = Bukkit.createWorld(creator);
        if (world != null) {
            world.setSpawnLocation(spawnX, spawnY, spawnZ);
            plugin.getLogger().info("Monde des Iles '" + worldName + "' cree (void).");
        } else {
            plugin.getLogger().severe("Impossible de creer le monde des Iles '" + worldName + "'.");
        }
    }

    private void loadIslands() {
        islandsByOwner.clear();
        islandsByIndex.clear();
        Connection connection = database.getConnection();
        String select = "SELECT uuid_proprietaire, position_index, centre_x, centre_y, centre_z, "
                + "taille, valeur, dernier_palier, home_x, home_y, home_z, home_yaw, home_pitch FROM iles;";
        try (PreparedStatement statement = connection.prepareStatement(select);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Island island = new Island();
                island.owner = UUID.fromString(rs.getString("uuid_proprietaire"));
                island.index = rs.getInt("position_index");
                island.centerX = rs.getInt("centre_x");
                island.centerY = rs.getInt("centre_y");
                island.centerZ = rs.getInt("centre_z");
                island.size = rs.getInt("taille");
                island.value = rs.getLong("valeur");
                island.lastTierIndex = rs.getInt("dernier_palier");
                double homeX = rs.getDouble("home_x");
                if (!rs.wasNull()) {
                    island.homeX = homeX;
                    island.homeY = rs.getDouble("home_y");
                    island.homeZ = rs.getDouble("home_z");
                    island.homeYaw = rs.getFloat("home_yaw");
                    island.homePitch = rs.getFloat("home_pitch");
                }
                islandsByOwner.put(island.owner, island);
                islandsByIndex.put(island.index, island);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur chargement des iles : " + e.getMessage());
        }

        for (Island island : islandsByOwner.values()) {
            loadMembers(island);
            loadCompletedChallenges(island);
        }
        plugin.getLogger().info(islandsByOwner.size() + " ile(s) chargee(s) depuis la base.");
    }

    private void loadCompletedChallenges(Island island) {
        Connection connection = database.getConnection();
        String select = "SELECT defi_id FROM ile_defis_completes WHERE uuid_proprietaire = ?;";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, island.owner.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    island.completedChallenges.add(rs.getString("defi_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur chargement defis completes pour " + island.owner + " : " + e.getMessage());
        }
    }

    private void loadMembers(Island island) {
        Connection connection = database.getConnection();
        String select = "SELECT uuid_membre FROM ile_membres WHERE uuid_proprietaire = ?;";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, island.owner.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    try {
                        island.members.add(UUID.fromString(rs.getString("uuid_membre")));
                    } catch (IllegalArgumentException ignored) {
                        // Membre corrompu : ignore silencieusement.
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur chargement membres ile de " + island.owner + " : " + e.getMessage());
        }
    }

    // ---- Creation / suppression ----

    public boolean hasIsland(UUID owner) {
        return islandsByOwner.containsKey(owner);
    }

    public Island getIsland(UUID owner) {
        return islandsByOwner.get(owner);
    }

    public World getWorld() {
        return world;
    }

    public Location getWorldSpawn() {
        return new Location(world, spawnX + 0.5, spawnY + 1, spawnZ + 0.5);
    }

    /** Cree une nouvelle ile pour ce joueur a une position jamais utilisee (index atomique
     * persiste), a sa taille de depart. Ne construit PAS la plateforme (voir IslandService). */
    public synchronized Island createIsland(UUID owner) {
        if (hasIsland(owner)) {
            return islandsByOwner.get(owner);
        }
        int index = nextIndex();
        Island island = new Island();
        island.owner = owner;
        island.index = index;
        island.centerX = index * spacing;
        island.centerY = spawnY;
        island.centerZ = 0;
        island.size = startSize;
        island.value = 0;
        island.lastTierIndex = 0;

        islandsByOwner.put(owner, island);
        islandsByIndex.put(index, island);
        persistIslandAsync(island);
        return island;
    }

    private int nextIndex() {
        Connection connection = database.getConnection();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT prochain_index FROM ile_compteur WHERE id = 1;");
             ResultSet rs = select.executeQuery()) {
            int index = rs.next() ? rs.getInt("prochain_index") : 0;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE ile_compteur SET prochain_index = ? WHERE id = 1;")) {
                update.setInt(1, index + 1);
                update.executeUpdate();
            }
            return index;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur allocation position ile : " + e.getMessage());
            return islandsByIndex.size();
        }
    }

    /** Supprime l'ile de ce joueur (base + cache + membres). Ne modifie PAS le monde (les blocs
     * restent physiquement en place, simplement non protege/inaccessible via /ile). */
    public synchronized void deleteIsland(UUID owner) {
        Island island = islandsByOwner.remove(owner);
        if (island == null) {
            return;
        }
        islandsByIndex.remove(island.index);
        Connection connection = database.getConnection();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement d1 = connection.prepareStatement("DELETE FROM iles WHERE uuid_proprietaire = ?;");
                 PreparedStatement d2 = connection.prepareStatement("DELETE FROM ile_membres WHERE uuid_proprietaire = ?;")) {
                d1.setString(1, owner.toString());
                d1.executeUpdate();
                d2.setString(1, owner.toString());
                d2.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur suppression ile de " + owner + " : " + e.getMessage());
            }
        });
    }

    private void persistIslandAsync(Island island) {
        Connection connection = database.getConnection();
        int index = island.index;
        int x = island.centerX;
        int y = island.centerY;
        int z = island.centerZ;
        int size = island.size;
        long value = island.value;
        int lastTier = island.lastTierIndex;
        Double homeX = island.homeX;
        Double homeY = island.homeY;
        Double homeZ = island.homeZ;
        float homeYaw = island.homeYaw;
        float homePitch = island.homePitch;
        String ownerStr = island.owner.toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String upsert = "INSERT INTO iles (uuid_proprietaire, position_index, centre_x, centre_y, centre_z, "
                    + "taille, valeur, dernier_palier, home_x, home_y, home_z, home_yaw, home_pitch) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(uuid_proprietaire) DO UPDATE SET taille = excluded.taille, "
                    + "valeur = excluded.valeur, dernier_palier = excluded.dernier_palier, "
                    + "home_x = excluded.home_x, home_y = excluded.home_y, home_z = excluded.home_z, "
                    + "home_yaw = excluded.home_yaw, home_pitch = excluded.home_pitch;";
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, ownerStr);
                statement.setInt(2, index);
                statement.setInt(3, x);
                statement.setInt(4, y);
                statement.setInt(5, z);
                statement.setInt(6, size);
                statement.setLong(7, value);
                statement.setInt(8, lastTier);
                if (homeX != null) {
                    statement.setDouble(9, homeX);
                    statement.setDouble(10, homeY);
                    statement.setDouble(11, homeZ);
                } else {
                    statement.setNull(9, java.sql.Types.REAL);
                    statement.setNull(10, java.sql.Types.REAL);
                    statement.setNull(11, java.sql.Types.REAL);
                }
                statement.setFloat(12, homeYaw);
                statement.setFloat(13, homePitch);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur sauvegarde ile de " + ownerStr + " : " + e.getMessage());
            }
        });
    }

    // ---- Home personnalise (/ile sethome) ----

    /** Definit le point d'atterrissage personnalise de cette ile (doit etre valide dans les
     * limites de l'ile, verifie en amont par IslandService). */
    public void setHome(Island island, Location location) {
        island.homeX = location.getX();
        island.homeY = location.getY();
        island.homeZ = location.getZ();
        island.homeYaw = location.getYaw();
        island.homePitch = location.getPitch();
        persistIslandAsync(island);
    }

    /** Point d'atterrissage de cette ile : le home personnalise s'il existe, sinon le centre par
     * defaut (juste au-dessus de la plateforme de depart). */
    public Location getHomeLocation(Island island) {
        if (island.hasCustomHome()) {
            return new Location(world, island.homeX, island.homeY, island.homeZ, island.homeYaw, island.homePitch);
        }
        return new Location(world, island.centerX + 0.5, island.centerY + 2, island.centerZ + 0.5);
    }

    // ---- Recherche spatiale O(1) ----

    /** L'ile couvrant ce bloc (X/Z, tout Y confondu), ou null si aucune (void entre 2 iles). */
    public Island getIslandAt(int blockX, int blockZ) {
        int index = Math.round((float) blockX / spacing);
        Island island = islandsByIndex.get(index);
        if (island == null) {
            return null;
        }
        if (Math.abs(blockX - island.centerX) > island.size || Math.abs(blockZ - island.centerZ) > island.size) {
            return null;
        }
        return island;
    }

    // ---- Membres de confiance ----

    public void addMember(Island island, UUID member) {
        if (island.members.add(member)) {
            Connection connection = database.getConnection();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO ile_membres (uuid_proprietaire, uuid_membre) VALUES (?, ?) ON CONFLICT DO NOTHING;")) {
                    statement.setString(1, island.owner.toString());
                    statement.setString(2, member.toString());
                    statement.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erreur ajout membre ile : " + e.getMessage());
                }
            });
        }
    }

    public boolean removeMember(Island island, UUID member) {
        if (!island.members.remove(member)) {
            return false;
        }
        Connection connection = database.getConnection();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM ile_membres WHERE uuid_proprietaire = ? AND uuid_membre = ?;")) {
                statement.setString(1, island.owner.toString());
                statement.setString(2, member.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur retrait membre ile : " + e.getMessage());
            }
        });
        return true;
    }

    // ---- Agrandissement ----

    public boolean isMaxSize(Island island) {
        return island.size >= maxSize;
    }

    public double getUpgradePrice() {
        return upgradePrice;
    }

    public int getMaxSize() {
        return maxSize;
    }

    /** Agrandit le rayon protege (plafonne a taille-max). Renvoie le nouveau rayon. */
    public int upgrade(Island island) {
        island.size = Math.min(maxSize, island.size + blocksPerUpgrade);
        persistIslandAsync(island);
        return island.size;
    }

    // ---- Valeur / niveau / paliers ----

    public int getBlockValue(Material material) {
        return blockValues.getOrDefault(material, 0);
    }

    public boolean isPvpAllowed() {
        return pvpAllowed;
    }

    public boolean areExplosionsAllowed() {
        return explosionsAllowed;
    }

    /** Ajoute (ou retire, si negatif) de la valeur a cette ile, persiste, et renvoie la liste des
     * NOUVEAUX paliers atteints (a distribuer par IslandService). */
    public List<Reward> addValueAndCheckTiers(Island island, int delta) {
        island.value = Math.max(0, island.value + delta);
        List<Reward> newlyReached = new ArrayList<>();
        for (int i = island.lastTierIndex; i < tiers.size(); i++) {
            Tier tier = tiers.get(i);
            if (island.value >= tier.level()) {
                newlyReached.add(tier.reward());
                island.lastTierIndex = i + 1;
            } else {
                break;
            }
        }
        persistIslandAsync(island);
        return newlyReached;
    }

    // ---- Defis d'ile (voir "defis" dans islands.yml) ----

    /** Verifie tous les defis du type donne, renvoie ceux NOUVELLEMENT completes (jamais
     * accomplis avant) pour cette valeur courante, et les marque completes (persiste). */
    public List<Challenge> checkChallenges(Island island, ChallengeType type, long currentValue) {
        List<Challenge> newlyCompleted = new ArrayList<>();
        for (Challenge challenge : challenges) {
            if (challenge.type() != type || currentValue < challenge.objective()) {
                continue;
            }
            if (island.completedChallenges.add(challenge.id())) {
                newlyCompleted.add(challenge);
                persistChallengeCompletedAsync(island.owner, challenge.id());
            }
        }
        return newlyCompleted;
    }

    private void persistChallengeCompletedAsync(UUID owner, String challengeId) {
        Connection connection = database.getConnection();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ile_defis_completes (uuid_proprietaire, defi_id) VALUES (?, ?) ON CONFLICT DO NOTHING;")) {
                statement.setString(1, owner.toString());
                statement.setString(2, challengeId);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur sauvegarde defi complete pour " + owner + " : " + e.getMessage());
            }
        });
    }

    /** Toutes les iles, triees par valeur decroissante (pour /ile top). */
    public List<Island> getIslandsSortedByValue() {
        List<Island> all = new ArrayList<>(islandsByOwner.values());
        all.sort((a, b) -> Long.compare(b.value, a.value));
        return all;
    }

    public List<Map<?, ?>> getStarterKitRaw() {
        return islandsConfig.get().getMapList("kit-depart");
    }
}
