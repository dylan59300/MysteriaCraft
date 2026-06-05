package fr.vanadia.dungeons;

import fr.vanadia.Vanadia;
import fr.vanadia.mobs.CustomMob;
import fr.vanadia.mobs.MobManager;
import fr.vanadia.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class DungeonManager {

    private final Vanadia plugin;
    private final Map<String, Dungeon> dungeons;
    private final Map<UUID, DungeonInstance> playerDungeons;
    private final List<DungeonInstance> activeInstances;

    public DungeonManager(Vanadia plugin) {
        this.plugin = plugin;
        this.dungeons = new LinkedHashMap<>();
        this.playerDungeons = new HashMap<>();
        this.activeInstances = new ArrayList<>();
        loadDungeons();
    }

    private void loadDungeons() {
        dungeons.put("crypte-obscure", new Dungeon(
                "crypte-obscure",
                "&8Crypte Obscure",
                5, 4, 5,
                "golem-ancien",
                200,
                List.of("DIAMOND:3", "GOLDEN_APPLE:1")
        ));

        dungeons.put("foret-maudite", new Dungeon(
                "foret-maudite",
                "&2Foret Maudite",
                10, 4, 8,
                "zombie-sorcier",
                350,
                List.of("DIAMOND:5", "ENCHANTED_GOLDEN_APPLE:1")
        ));
    }

    public void reload() {
        dungeons.clear();
        loadDungeons();
    }

    public Dungeon getDungeon(String id) {
        return dungeons.get(id);
    }

    public Collection<Dungeon> getAllDungeons() {
        return dungeons.values();
    }

    public boolean enterDungeon(Player player, String dungeonId) {
        Dungeon dungeon = dungeons.get(dungeonId);
        if (dungeon == null) return false;

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) return false;

        if (data.getLevel() < dungeon.getMinLevel()) {
            plugin.getMessageUtil().send(player, "dungeon-level-required",
                    Map.of("level", String.valueOf(dungeon.getMinLevel())));
            return false;
        }

        if (playerDungeons.containsKey(player.getUniqueId())) {
            return false;
        }

        DungeonInstance instance = new DungeonInstance(dungeonId, player.getLocation());
        instance.addPlayer(player.getUniqueId());
        playerDungeons.put(player.getUniqueId(), instance);
        activeInstances.add(instance);

        plugin.getMessageUtil().send(player, "dungeon-enter",
                Map.of("dungeon", dungeon.getDisplayName()));

        startDungeonWaves(instance, dungeon, player);
        return true;
    }

    private void startDungeonWaves(DungeonInstance instance, Dungeon dungeon, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!instance.isActive() || !player.isOnline()) {
                    cancel();
                    return;
                }

                instance.nextWave();
                int wave = instance.getCurrentWave();

                if (wave > dungeon.getMobWaves()) {
                    spawnBoss(instance, dungeon, player);
                    cancel();
                    return;
                }

                plugin.getMessageUtil().send(player, "dungeon-wave",
                        Map.of("wave", String.valueOf(wave),
                               "max_wave", String.valueOf(dungeon.getMobWaves())));

                spawnWaveMobs(player.getLocation(), wave);
            }
        }.runTaskTimer(plugin, 40L, 200L);
    }

    private void spawnWaveMobs(Location location, int wave) {
        int mobCount = 3 + wave;
        EntityType[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};

        for (int i = 0; i < mobCount; i++) {
            Location spawnLoc = location.clone().add(
                    (Math.random() - 0.5) * 10,
                    0,
                    (Math.random() - 0.5) * 10
            );
            EntityType type = types[(int) (Math.random() * types.length)];
            location.getWorld().spawnEntity(spawnLoc, type);
        }
    }

    private void spawnBoss(DungeonInstance instance, Dungeon dungeon, Player player) {
        instance.setBossSpawned(true);
        CustomMob boss = plugin.getMobManager().getCustomMob(dungeon.getBossId());
        if (boss == null) return;

        Location loc = player.getLocation().add(5, 0, 0);
        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, boss.getBaseEntity());
        plugin.getMobManager().applyCustomMob(entity, boss);

        plugin.getMessageUtil().send(player, "dungeon-boss",
                Map.of("boss", boss.getDisplayName()));
    }

    public void completeDungeon(Player player) {
        DungeonInstance instance = playerDungeons.get(player.getUniqueId());
        if (instance == null) return;

        Dungeon dungeon = dungeons.get(instance.getDungeonId());
        if (dungeon == null) return;

        instance.setActive(false);
        playerDungeons.remove(player.getUniqueId());
        activeInstances.remove(instance);

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data != null) {
            data.incrementDungeonsCompleted();
            boolean leveledUp = data.addXp(dungeon.getXpReward());
            if (leveledUp) {
                plugin.getMessageUtil().send(player, "level-up",
                        Map.of("level", String.valueOf(data.getLevel())));
                plugin.getClassManager().applyClassStats(player, data);
            }
        }

        plugin.getMessageUtil().send(player, "dungeon-completed",
                Map.of("dungeon", dungeon.getDisplayName()));
    }

    public void leaveDungeon(Player player) {
        DungeonInstance instance = playerDungeons.get(player.getUniqueId());
        if (instance == null) return;

        instance.removePlayer(player.getUniqueId());
        instance.setActive(false);
        playerDungeons.remove(player.getUniqueId());
        activeInstances.remove(instance);

        player.teleport(instance.getSpawnLocation());
        plugin.getMessageUtil().send(player, "dungeon-leave");
    }

    public boolean isInDungeon(UUID uuid) {
        return playerDungeons.containsKey(uuid);
    }

    public DungeonInstance getPlayerInstance(UUID uuid) {
        return playerDungeons.get(uuid);
    }
}
