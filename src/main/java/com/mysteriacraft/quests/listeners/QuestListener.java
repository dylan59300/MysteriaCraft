package com.mysteriacraft.quests.listeners;

import com.mysteriacraft.quests.QuestService;
import com.mysteriacraft.quests.QuestType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Traduit les evenements Bukkit pertinents en progression de quetes.
 */
public class QuestListener implements Listener {

    private final QuestService questService;

    public QuestListener(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        questService.registerProgress(event.getPlayer(), QuestType.BREAK_BLOCK, event.getBlock().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        questService.registerProgress(event.getPlayer(), QuestType.PLACE_BLOCK, event.getBlock().getType().name(), 1);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            questService.registerProgress(killer, QuestType.KILL_MOB, event.getEntityType().name(), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        questService.registerProgress(event.getPlayer(), QuestType.FISH, null, 1);
    }
}
