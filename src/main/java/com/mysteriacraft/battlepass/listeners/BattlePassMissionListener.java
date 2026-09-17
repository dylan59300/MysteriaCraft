package com.mysteriacraft.battlepass.listeners;

import com.mysteriacraft.battlepass.BattlePassMissionManager;
import com.mysteriacraft.battlepass.BattlePassMissionManager.Mission;
import com.mysteriacraft.battlepass.BattlePassMissionManager.MissionType;
import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fait progresser les missions quotidiennes du BattlePass (voir BattlePassMissionManager) au fil
 * du jeu : miner un bloc, tuer un mob, pecher un poisson, crafter un objet, poser un bloc,
 * enchanter un objet, tondre un mouton.
 */
public class BattlePassMissionListener implements Listener {

    private final Plugin plugin;
    private final BattlePassMissionManager missionManager;
    private final BattlePassService battlePassService;
    private final MessageManager messages;

    public BattlePassMissionListener(Plugin plugin, BattlePassMissionManager missionManager,
                                      BattlePassService battlePassService, MessageManager messages) {
        this.plugin = plugin;
        this.missionManager = missionManager;
        this.battlePassService = battlePassService;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String materiel = event.getBlock().getType().name();
        progress(player, MissionType.MINER, materiel, 1);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        progress(killer, MissionType.TUER, event.getEntityType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        progress(event.getPlayer(), MissionType.PECHER, "POISSON", 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getRecipe().getResult().getType().isAir() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        progress(player, MissionType.CRAFT, event.getRecipe().getResult().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        progress(event.getPlayer(), MissionType.PLACER, event.getBlockPlaced().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        progress(event.getEnchanter(), MissionType.ENCHANTER, "OBJET", 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (event.getEntity().getType() != EntityType.SHEEP) {
            return;
        }
        progress(event.getPlayer(), MissionType.TONDRE, "MOUTON", 1);
    }

    private void progress(Player player, MissionType type, String cible, int amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Mission> completed = missionManager.recordProgress(player.getUniqueId(), type, cible, amount);
            if (completed.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Mission mission : completed) {
                    battlePassService.addXp(player, mission.xpRecompense());
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("xp", String.valueOf(mission.xpRecompense()));
                    messages.send(player, "battlepass.mission-terminee", placeholders);
                }
            });
        });
    }
}
