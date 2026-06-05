package fr.vanadia.listeners;

import fr.vanadia.Vanadia;
import fr.vanadia.mobs.CustomMob;
import fr.vanadia.mobs.MobManager;
import fr.vanadia.player.PlayerData;
import fr.vanadia.quests.Quest;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class CombatListener implements Listener {

    private final Vanadia plugin;

    public CombatListener(Vanadia plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getClassType() == null) return;

        double bonusDamage = plugin.getClassManager().calculateDamage(data) - data.getClassType().getBaseDamage();
        if (bonusDamage > 0) {
            event.setDamage(event.getDamage() + bonusDamage);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getClassType() == null) return;

        double defense = plugin.getClassManager().calculateDefense(data);
        double reduction = defense / (defense + 50.0);
        event.setDamage(event.getDamage() * (1.0 - reduction));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        PlayerData data = plugin.getPlayerManager().getPlayerData(killer.getUniqueId());
        if (data == null) return;

        int xpReward;
        String mobName;

        if (plugin.getMobManager().isCustomMob(entity)) {
            CustomMob customMob = plugin.getMobManager().getCustomMobFromEntity(entity);
            if (customMob != null) {
                xpReward = customMob.getXpReward();
                mobName = customMob.getDisplayName();

                event.getDrops().clear();
                for (String drop : customMob.getDrops()) {
                    String[] parts = drop.split(":");
                    Material material = Material.valueOf(parts[0]);
                    int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    event.getDrops().add(new ItemStack(material, amount));
                }
            } else {
                xpReward = 10;
                mobName = entity.getType().name();
            }
        } else {
            xpReward = plugin.getConfig().getInt("levels.xp-mob-kill", 10);
            mobName = entity.getType().name();
        }

        boolean leveledUp = data.addXp(xpReward);
        plugin.getMessageUtil().send(killer, "mob-killed",
                Map.of("xp", String.valueOf(xpReward), "mob", mobName));

        if (leveledUp) {
            plugin.getMessageUtil().send(killer, "level-up",
                    Map.of("level", String.valueOf(data.getLevel())));
            plugin.getClassManager().applyClassStats(killer, data);
        }

        plugin.getQuestManager().updateProgress(killer,
                Quest.QuestType.KILL_MOB, entity.getType().name(), 1);
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;

        LivingEntity entity = event.getEntity();
        EntityType type = entity.getType();

        if (plugin.getMobManager().shouldSpawnCustom(type)) {
            CustomMob customMob = plugin.getMobManager().getRandomMobForEntity(type);
            if (customMob != null) {
                plugin.getMobManager().applyCustomMob(entity, customMob);
            }
        }
    }
}
