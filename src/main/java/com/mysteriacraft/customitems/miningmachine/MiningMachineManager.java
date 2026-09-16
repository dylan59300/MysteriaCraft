package com.mysteriacraft.customitems.miningmachine;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Charge la configuration de la Machine a Miner (mining_machine.yml) et suit l'etat de chaque
 * machine posee : proprietaire, carburant restant (en "blocs minables"), curseur de progression
 * dans le chunk en cours, si elle est active, et son hologramme. Comme MachineManager (Machine a
 * Transformation), un Block "nu" n'a pas de PersistentDataContainer sur paper-api 1.20.1 : l'etat
 * est donc suivi via une table SQLite dediee indexee par position, mise en cache memoire
 * (write-through).
 */
public class MiningMachineManager {

    /** Un type de carburant utilisable : nombre de blocs qu'il permet de miner. */
    public record FuelType(String itemId, int blocs) {
    }

    private static final class MachineState {
        UUID owner;
        int fuelBlocks;
        boolean active;
        /** Index dans l'ordre de parcours du chunk (x*16+z)*hauteur + offsetY ; -1 = jamais demarree. */
        long cursor = -1;
        /** Coin (chunk X/Z * 16) du chunk actuellement mine, fige au demarrage d'une passe. */
        int chunkOriginX;
        int chunkOriginZ;
        long totalBlocks;
        long minedBlocks;
        UUID hologramUuid;
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager configManager;
    private final NamespacedKey machineKey;

    private final Map<Location, MachineState> machines = new ConcurrentHashMap<>();
    private final Map<String, FuelType> fuelTypes = new LinkedHashMap<>();
    private final Set<Material> ignoredMaterials = EnumSet.noneOf(Material.class);

    private Material blockMaterial = Material.DRIPSTONE_BLOCK;
    private int largeur = 16;
    private int hauteurMin = -32;
    private int hauteurMax = 64;
    private int blocsParTick = 40;
    private int maxPerPlayer = 5;

    public MiningMachineManager(Plugin plugin, Database database, ConfigManager configManager) {
        this.plugin = plugin;
        this.database = database;
        this.configManager = configManager;
        this.machineKey = new NamespacedKey(plugin, "machine-a-miner");
        createTable();
        loadMachines();
        loadConfig();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS machines_minieres (" +
                "monde TEXT NOT NULL, " +
                "x INTEGER NOT NULL, " +
                "y INTEGER NOT NULL, " +
                "z INTEGER NOT NULL, " +
                "proprietaire TEXT, " +
                "carburant INTEGER NOT NULL DEFAULT 0, " +
                "actif INTEGER NOT NULL DEFAULT 0, " +
                "curseur INTEGER NOT NULL DEFAULT -1, " +
                "chunk_origine_x INTEGER NOT NULL DEFAULT 0, " +
                "chunk_origine_z INTEGER NOT NULL DEFAULT 0, " +
                "total_blocs INTEGER NOT NULL DEFAULT 0, " +
                "blocs_mines INTEGER NOT NULL DEFAULT 0, " +
                "hologramme_uuid TEXT, " +
                "PRIMARY KEY (monde, x, y, z)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'machines_minieres' : " + e.getMessage());
        }
    }

    private void loadMachines() {
        machines.clear();
        String select = "SELECT monde, x, y, z, proprietaire, carburant, actif, curseur, chunk_origine_x, "
                + "chunk_origine_z, total_blocs, blocs_mines, hologramme_uuid FROM machines_minieres;";
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
                String ownerRaw = rs.getString("proprietaire");
                if (ownerRaw != null) {
                    try {
                        state.owner = UUID.fromString(ownerRaw);
                    } catch (IllegalArgumentException ignored) {
                        // UUID corrompu : la machine reste sans proprietaire connu.
                    }
                }
                state.fuelBlocks = rs.getInt("carburant");
                state.active = rs.getInt("actif") != 0;
                state.cursor = rs.getLong("curseur");
                state.chunkOriginX = rs.getInt("chunk_origine_x");
                state.chunkOriginZ = rs.getInt("chunk_origine_z");
                state.totalBlocks = rs.getLong("total_blocs");
                state.minedBlocks = rs.getLong("blocs_mines");
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
            plugin.getLogger().severe("Erreur chargement des Machines a Miner : " + e.getMessage());
        }
        plugin.getLogger().info(machines.size() + " Machine(s) a Miner rechargee(s) depuis la base.");
    }

    private void persistAsync(Location location, MachineState state) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String upsert = "INSERT INTO machines_minieres (monde, x, y, z, proprietaire, carburant, actif, curseur, "
                    + "chunk_origine_x, chunk_origine_z, total_blocs, blocs_mines, hologramme_uuid) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(monde, x, y, z) DO UPDATE SET proprietaire = excluded.proprietaire, "
                    + "carburant = excluded.carburant, actif = excluded.actif, curseur = excluded.curseur, "
                    + "chunk_origine_x = excluded.chunk_origine_x, chunk_origine_z = excluded.chunk_origine_z, "
                    + "total_blocs = excluded.total_blocs, blocs_mines = excluded.blocs_mines, "
                    + "hologramme_uuid = excluded.hologramme_uuid;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, location.getWorld().getName());
                statement.setInt(2, location.getBlockX());
                statement.setInt(3, location.getBlockY());
                statement.setInt(4, location.getBlockZ());
                statement.setString(5, state.owner != null ? state.owner.toString() : null);
                statement.setInt(6, state.fuelBlocks);
                statement.setInt(7, state.active ? 1 : 0);
                statement.setLong(8, state.cursor);
                statement.setInt(9, state.chunkOriginX);
                statement.setInt(10, state.chunkOriginZ);
                statement.setLong(11, state.totalBlocks);
                statement.setLong(12, state.minedBlocks);
                statement.setString(13, state.hologramUuid != null ? state.hologramUuid.toString() : null);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur sauvegarde machine a miner : " + e.getMessage());
            }
        });
    }

    private void deleteAsync(Location location) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String delete = "DELETE FROM machines_minieres WHERE monde = ? AND x = ? AND y = ? AND z = ?;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                statement.setString(1, location.getWorld().getName());
                statement.setInt(2, location.getBlockX());
                statement.setInt(3, location.getBlockY());
                statement.setInt(4, location.getBlockZ());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur suppression machine a miner : " + e.getMessage());
            }
        });
    }

    private static Location blockKey(Block block) {
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    public void loadConfig() {
        fuelTypes.clear();
        ignoredMaterials.clear();

        ConfigurationSection section = configManager.get().getConfigurationSection("machine-a-miner");
        if (section == null) {
            plugin.getLogger().warning("Section 'machine-a-miner' manquante dans mining_machine.yml.");
            return;
        }

        Material configuredBlock = Material.matchMaterial(section.getString("bloc", "DRIPSTONE_BLOCK"));
        blockMaterial = configuredBlock != null ? configuredBlock : Material.DRIPSTONE_BLOCK;

        largeur = Math.max(1, Math.min(16, section.getInt("largeur", 16)));
        hauteurMin = section.getInt("hauteur-min", -32);
        hauteurMax = Math.max(hauteurMin + 1, section.getInt("hauteur-max", 64));
        blocsParTick = Math.max(1, section.getInt("blocs-par-tick", 40));
        maxPerPlayer = Math.max(0, section.getInt("max-machines-par-joueur", 5));

        ConfigurationSection carburants = section.getConfigurationSection("carburants");
        if (carburants != null) {
            for (String itemId : carburants.getKeys(false)) {
                ConfigurationSection fuelSection = carburants.getConfigurationSection(itemId);
                if (fuelSection == null) {
                    continue;
                }
                int blocs = Math.max(1, fuelSection.getInt("blocs", 500));
                fuelTypes.put(itemId.toLowerCase(), new FuelType(itemId.toLowerCase(), blocs));
            }
        }

        for (String materialName : section.getStringList("blocs-ignores")) {
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                ignoredMaterials.add(material);
            }
        }
        // Toujours ignores, quelle que soit la config : impossible de miner l'air ou le bedrock.
        ignoredMaterials.add(Material.AIR);
        ignoredMaterials.add(Material.CAVE_AIR);
        ignoredMaterials.add(Material.VOID_AIR);
        ignoredMaterials.add(Material.BEDROCK);

        plugin.getLogger().info("Machine a Miner : " + fuelTypes.size() + " type(s) de carburant, "
                + "zone " + largeur + "x" + largeur + " de Y" + hauteurMin + " a Y" + hauteurMax
                + ", max " + maxPerPlayer + " par joueur.");
    }

    public Material getBlockMaterial() {
        return blockMaterial;
    }

    public int getLargeur() {
        return largeur;
    }

    public int getHauteurMin() {
        return hauteurMin;
    }

    public int getHauteurMax() {
        return hauteurMax;
    }

    public int getBlocsParTick() {
        return blocsParTick;
    }

    public int getMaxPerPlayer() {
        return maxPerPlayer;
    }

    public boolean isIgnored(Material material) {
        return ignoredMaterials.contains(material);
    }

    public FuelType getFuelType(String itemId) {
        return itemId == null ? null : fuelTypes.get(itemId.toLowerCase());
    }

    public FuelType getDefaultFuelType() {
        return fuelTypes.values().stream().findFirst().orElse(null);
    }

    // ---- Item / marquage du bloc ----

    public ItemStack createMachineItem() {
        return createMachineItem(1);
    }

    public ItemStack createMachineItem(int amount) {
        ItemStack item = new ItemStack(blockMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.color("&b&lMachine a Miner"));
            meta.setLore(List.of(
                    MessageManager.color("&7Mine automatiquement un chunk entier"),
                    MessageManager.color("&7(" + largeur + "x" + largeur + ", de Y" + hauteurMin + " a Y" + hauteurMax + ")."),
                    MessageManager.color("&7Clic-droit avec du carburant pour"),
                    MessageManager.color("&7(re)demarrer/alimenter le minage."),
                    MessageManager.color("&7Clic a vide pour voir sa progression.")
            ));
            meta.getPersistentDataContainer().set(machineKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isMachineItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(machineKey, PersistentDataType.BYTE);
    }

    /** Marque un bloc pose comme etant une Machine a Miner appartenant a ce joueur. */
    public void tagBlock(Block block, UUID owner) {
        Location key = blockKey(block);
        MachineState state = new MachineState();
        state.owner = owner;
        machines.put(key, state);
        persistAsync(key, state);
    }

    public void forgetMachine(Location location) {
        machines.remove(location);
        deleteAsync(location);
    }

    public boolean isMachineBlock(Block block) {
        return machines.containsKey(blockKey(block));
    }

    public Set<Location> getActiveMachineLocations() {
        return machines.keySet();
    }

    /** UUID du proprietaire de cette machine (celui qui l'a posee), ou null si inconnu. */
    public UUID getOwner(Block block) {
        MachineState state = machines.get(blockKey(block));
        return state == null ? null : state.owner;
    }

    /** Nombre de Machines a Miner actuellement suivies appartenant a ce joueur (chargees au
     * demarrage + posees depuis). */
    public int countOwnedMachines(UUID owner) {
        int count = 0;
        for (MachineState state : machines.values()) {
            if (owner.equals(state.owner)) {
                count++;
            }
        }
        return count;
    }

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    /** Premier coffre/coffre piege/baril/hopper colle a la machine (sortie des blocs mines), ou
     * null si aucun n'est colle (les blocs mines sont alors deposes au sol au pied de la machine). */
    public Block getOutputContainer(Block machineBlock) {
        for (BlockFace face : ADJACENT_FACES) {
            Block relative = machineBlock.getRelative(face);
            Material type = relative.getType();
            if (type == Material.CHEST || type == Material.TRAPPED_CHEST
                    || type == Material.BARREL || type == Material.HOPPER) {
                return relative;
            }
        }
        return null;
    }

    // ---- Nettoyage d'un ancien hologramme (fonctionnalite retiree : plus d'ArmorStand affiche
    // au-dessus des machines) ----

    public ArmorStand getHologram(Block block) {
        MachineState state = machines.get(blockKey(block));
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
        MachineState state = machines.get(blockKey(block));
        if (state != null) {
            state.hologramUuid = null;
        }
    }

    // ---- Etat (carburant, progression) ----

    public int getFuel(Block block) {
        MachineState state = machines.get(blockKey(block));
        return state == null ? 0 : state.fuelBlocks;
    }

    public boolean isActive(Block block) {
        MachineState state = machines.get(blockKey(block));
        return state != null && state.active;
    }

    public long getMinedBlocks(Block block) {
        MachineState state = machines.get(blockKey(block));
        return state == null ? 0 : state.minedBlocks;
    }

    public long getTotalBlocks(Block block) {
        MachineState state = machines.get(blockKey(block));
        return state == null ? 0 : state.totalBlocks;
    }

    /**
     * Ajoute du carburant (en blocs minables). Si la machine n'etait pas deja en train de miner,
     * (re)demarre une passe FRAICHE sur le chunk actuel (curseur remis a 0). Si elle etait deja
     * active, le carburant s'ajoute simplement au total restant sans perturber la progression.
     */
    public void refuel(Block block, int blocs) {
        Location key = blockKey(block);
        MachineState state = machines.get(key);
        if (state == null) {
            return;
        }
        state.fuelBlocks += blocs;
        if (!state.active) {
            state.active = true;
            state.cursor = 0;
            state.minedBlocks = 0;
            Chunk chunk = block.getChunk();
            state.chunkOriginX = chunk.getX() * 16;
            state.chunkOriginZ = chunk.getZ() * 16;
            state.totalBlocks = (long) largeur * largeur * (hauteurMax - hauteurMin);
        }
        persistAsync(key, state);
    }

    /** Arrete completement la passe en cours (sneak + clic-droit a vide) : le carburant restant est conserve. */
    public void stop(Block block) {
        Location key = blockKey(block);
        MachineState state = machines.get(key);
        if (state == null) {
            return;
        }
        state.active = false;
        state.cursor = -1;
        persistAsync(key, state);
    }

    /**
     * Mine jusqu'a blocsParTick blocs a partir du curseur actuel de cette machine (s'arrete plus
     * tot si le carburant est epuise ou si le chunk est entierement parcouru, auquel cas la
     * machine repasse a l'arret). Appele periodiquement par MiningMachineService pour chaque
     * machine active ; onBlockMined est invoque pour chaque bloc reellement mine (bloc maintenant
     * remplace par de l'air, materiau d'origine transmis pour deposer l'item correspondant).
     */
    public void tick(Block machineBlock, BiConsumer<Block, Material> onBlockMined) {
        Location key = blockKey(machineBlock);
        MachineState state = machines.get(key);
        if (state == null || !state.active) {
            return;
        }

        World world = machineBlock.getWorld();
        int processed = 0;
        while (processed < blocsParTick && state.cursor < state.totalBlocks) {
            if (state.fuelBlocks <= 0) {
                break;
            }
            int heightRange = hauteurMax - hauteurMin;
            long index = state.cursor;
            int localX = (int) (index / ((long) largeur * heightRange));
            long remainder = index % ((long) largeur * heightRange);
            int localZ = (int) (remainder / heightRange);
            int yOffset = (int) (remainder % heightRange);
            int worldX = state.chunkOriginX + localX;
            int worldZ = state.chunkOriginZ + localZ;
            int worldY = hauteurMax - 1 - yOffset;

            state.cursor++;
            processed++;

            if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) {
                continue;
            }
            Block target = world.getBlockAt(worldX, worldY, worldZ);
            Material type = target.getType();
            if (ignoredMaterials.contains(type) || isMachineBlock(target)) {
                continue;
            }

            target.setType(Material.AIR, false);
            state.fuelBlocks--;
            state.minedBlocks++;
            onBlockMined.accept(target, type);
        }

        if (state.cursor >= state.totalBlocks) {
            state.active = false;
            state.cursor = -1;
        }
        persistAsync(key, state);
    }
}
