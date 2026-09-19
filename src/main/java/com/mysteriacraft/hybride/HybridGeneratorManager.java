package com.mysteriacraft.hybride;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generateur Hybride : module standalone (parallele a com.mysteriacraft.customitems.generator,
 * volontairement independant pour ne pas risquer de regression sur le systeme existant) qui
 * produit DEUX ressources DIFFERENTES par cycle au lieu d'une seule. Meme principe d'accumulation
 * en temps reel que les autres generateurs (voir GeneratorManager#accrue pour la reference).
 */
public class HybridGeneratorManager {

    /** Une des deux sorties d'un type de generateur hybride : materiau vanilla ou item custom. */
    public record Sortie(Material materiau, String customItemId, int montantParCycle, double stockageMax) {
    }

    public record GeneratorType(String id, String nom, Material bloc, Sortie sortieA, Sortie sortieB, long intervalleSecondes) {
    }

    private static final class GeneratorState {
        String typeId;
        UUID owner;
        double stockA;
        double stockB;
        long lastTickMillis;
    }

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager hybrideConfig;
    private final NamespacedKey typeKey;
    private final Map<String, GeneratorType> types = new LinkedHashMap<>();
    private final Map<Location, GeneratorState> generators = new ConcurrentHashMap<>();

    public HybridGeneratorManager(Plugin plugin, Database database, ConfigManager hybrideConfig) {
        this.plugin = plugin;
        this.database = database;
        this.hybrideConfig = hybrideConfig;
        this.typeKey = new NamespacedKey(plugin, "generateurhybride-type");
        createTable();
        loadState();
        loadTypes();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS generateurs_hybrides (" +
                "monde TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, " +
                "type_id TEXT NOT NULL, owner TEXT, stock_a REAL NOT NULL DEFAULT 0, stock_b REAL NOT NULL DEFAULT 0, " +
                "last_tick_millis INTEGER NOT NULL, PRIMARY KEY (monde, x, y, z));";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'generateurs_hybrides' : " + e.getMessage());
        }
    }

    private void loadState() {
        generators.clear();
        String select = "SELECT monde, x, y, z, type_id, owner, stock_a, stock_b, last_tick_millis FROM generateurs_hybrides;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                org.bukkit.World world = Bukkit.getWorld(rs.getString("monde"));
                if (world == null) {
                    continue;
                }
                Location location = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                GeneratorState state = new GeneratorState();
                state.typeId = rs.getString("type_id");
                String ownerStr = rs.getString("owner");
                state.owner = ownerStr != null ? UUID.fromString(ownerStr) : null;
                state.stockA = rs.getDouble("stock_a");
                state.stockB = rs.getDouble("stock_b");
                state.lastTickMillis = rs.getLong("last_tick_millis");
                generators.put(location, state);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur chargement generateurs hybrides : " + e.getMessage());
        }
        plugin.getLogger().info(generators.size() + " generateur(s) hybride(s) recharge(s) depuis la base.");
    }

    private void persistAsync(Location key, GeneratorState state) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String upsert = "INSERT INTO generateurs_hybrides (monde, x, y, z, type_id, owner, stock_a, stock_b, last_tick_millis) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(monde, x, y, z) DO UPDATE SET " +
                    "type_id = excluded.type_id, owner = excluded.owner, stock_a = excluded.stock_a, " +
                    "stock_b = excluded.stock_b, last_tick_millis = excluded.last_tick_millis;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, key.getWorld().getName());
                statement.setInt(2, key.getBlockX());
                statement.setInt(3, key.getBlockY());
                statement.setInt(4, key.getBlockZ());
                statement.setString(5, state.typeId);
                statement.setString(6, state.owner != null ? state.owner.toString() : null);
                statement.setDouble(7, state.stockA);
                statement.setDouble(8, state.stockB);
                statement.setLong(9, state.lastTickMillis);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur sauvegarde generateur hybride : " + e.getMessage());
            }
        });
    }

    private void deleteAsync(Location key) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String delete = "DELETE FROM generateurs_hybrides WHERE monde = ? AND x = ? AND y = ? AND z = ?;";
            Connection connection = database.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                statement.setString(1, key.getWorld().getName());
                statement.setInt(2, key.getBlockX());
                statement.setInt(3, key.getBlockY());
                statement.setInt(4, key.getBlockZ());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur suppression generateur hybride : " + e.getMessage());
            }
        });
    }

    public void loadTypes() {
        types.clear();
        ConfigurationSection root = hybrideConfig.get().getConfigurationSection("types");
        if (root == null) {
            plugin.getLogger().warning("Aucun type de generateur hybride trouve (section 'types' manquante).");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                String nom = section.getString("nom", id);
                Material bloc = Material.matchMaterial(section.getString("bloc", "IRON_BLOCK"));
                long intervalle = Math.max(1, section.getLong("intervalle-secondes", 60));
                Sortie sortieA = parseSortie(section.getConfigurationSection("ressource-a"));
                Sortie sortieB = parseSortie(section.getConfigurationSection("ressource-b"));
                if (bloc == null || sortieA == null || sortieB == null) {
                    plugin.getLogger().warning("Type de generateur hybride '" + id + "' ignore : configuration invalide.");
                    continue;
                }
                types.put(id.toLowerCase(), new GeneratorType(id.toLowerCase(), nom, bloc, sortieA, sortieB, intervalle));
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement generateur hybride '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(types.size() + " type(s) de generateur hybride charge(s).");
    }

    private Sortie parseSortie(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String materielRaw = section.getString("objet-resultat");
        Material materiau = materielRaw != null ? Material.matchMaterial(materielRaw) : null;
        String customItemId = section.getString("objet-resultat-custom");
        if (materiau == null && customItemId == null) {
            return null;
        }
        int montant = Math.max(1, section.getInt("montant", 1));
        double stockageMax = section.getDouble("stockage-max", montant * 10.0);
        return new Sortie(materiau, customItemId, montant, stockageMax);
    }

    public GeneratorType getType(String id) {
        return id == null ? null : types.get(id.toLowerCase());
    }

    public Set<String> getTypeIds() {
        return types.keySet();
    }

    public ItemStack createGeneratorItem(GeneratorType type, int amount) {
        ItemStack item = new ItemBuilder(type.bloc(), amount)
                .name(type.nom())
                .lore(java.util.List.of(
                        MessageManager.color("&7Produit deux ressources en meme temps :"),
                        MessageManager.color("&7- " + sortieLabel(type.sortieA())),
                        MessageManager.color("&7- " + sortieLabel(type.sortieB())),
                        MessageManager.color("&7Clic-droit pour tout recuperer.")))
                .build();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String sortieLabel(Sortie sortie) {
        String nom = sortie.customItemId() != null ? sortie.customItemId() : sortie.materiau().name().replace('_', ' ');
        return sortie.montantParCycle() + "x " + nom;
    }

    public GeneratorType getItemGeneratorType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return getType(id);
    }

    public void tagBlock(Block block, GeneratorType type, UUID owner) {
        block.setType(type.bloc());
        Location key = blockKey(block);
        GeneratorState state = new GeneratorState();
        state.typeId = type.id();
        state.owner = owner;
        state.lastTickMillis = System.currentTimeMillis();
        generators.put(key, state);
        persistAsync(key, state);
    }

    public void forgetGenerator(Location location) {
        generators.remove(location);
        deleteAsync(location);
    }

    public boolean isGeneratorBlock(Block block) {
        return generators.containsKey(blockKey(block));
    }

    public GeneratorType getBlockType(Block block) {
        GeneratorState state = generators.get(blockKey(block));
        return state == null ? null : getType(state.typeId);
    }

    public Set<Location> getActiveGeneratorLocations() {
        return generators.keySet();
    }

    public int countOwnedGenerators(UUID owner) {
        int count = 0;
        for (GeneratorState state : generators.values()) {
            if (owner.equals(state.owner)) {
                count++;
            }
        }
        return count;
    }

    public Block getOutputContainer(Block generatorBlock) {
        for (BlockFace face : ADJACENT_FACES) {
            Block relative = generatorBlock.getRelative(face);
            Material material = relative.getType();
            if (material == Material.CHEST || material == Material.TRAPPED_CHEST
                    || material == Material.BARREL || material == Material.HOPPER) {
                return relative;
            }
        }
        return null;
    }

    /** Recalcule les deux stocks en fonction du temps ecoule reel, plafonnes independamment. */
    public void accrue(Block block) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        GeneratorType type = state == null ? null : getType(state.typeId);
        if (state == null || type == null) {
            return;
        }
        long now = System.currentTimeMillis();
        double elapsedSeconds = Math.max(0, (now - state.lastTickMillis) / 1000.0);
        double ratePerSecondA = type.sortieA().montantParCycle() / (double) type.intervalleSecondes();
        double ratePerSecondB = type.sortieB().montantParCycle() / (double) type.intervalleSecondes();
        state.stockA = Math.min(type.sortieA().stockageMax(), state.stockA + elapsedSeconds * ratePerSecondA);
        state.stockB = Math.min(type.sortieB().stockageMax(), state.stockB + elapsedSeconds * ratePerSecondB);
        state.lastTickMillis = now;
        persistAsync(key, state);
    }

    public int collectWholeA(Block block) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null) {
            return 0;
        }
        int whole = (int) Math.floor(state.stockA);
        if (whole <= 0) {
            return 0;
        }
        state.stockA -= whole;
        persistAsync(key, state);
        return whole;
    }

    public int collectWholeB(Block block) {
        Location key = blockKey(block);
        GeneratorState state = generators.get(key);
        if (state == null) {
            return 0;
        }
        int whole = (int) Math.floor(state.stockB);
        if (whole <= 0) {
            return 0;
        }
        state.stockB -= whole;
        persistAsync(key, state);
        return whole;
    }

    public void addStockA(Block block, double amount) {
        GeneratorState state = generators.get(blockKey(block));
        if (state != null) {
            state.stockA += amount;
            persistAsync(blockKey(block), state);
        }
    }

    public void addStockB(Block block, double amount) {
        GeneratorState state = generators.get(blockKey(block));
        if (state != null) {
            state.stockB += amount;
            persistAsync(blockKey(block), state);
        }
    }

    private static Location blockKey(Block block) {
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}
