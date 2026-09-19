package com.mysteriacraft.mobscustom.listeners;

import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.mobscustom.MobManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** Distribue la loot table dediee d'un mob custom au joueur qui l'a tue. */
public class MobListener implements Listener {

    private final MobManager manager;
    private final RewardGiver rewardGiver;

    public MobListener(MobManager manager, RewardGiver rewardGiver) {
        this.manager = manager;
        this.rewardGiver = rewardGiver;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        String mobId = manager.getMobId(event.getEntity());
        Player killer = event.getEntity().getKiller();
        if (mobId == null || killer == null) {
            return;
        }
        MobManager.MobDefinition definition = manager.getMob(mobId);
        if (definition == null) {
            return;
        }
        rewardGiver.giveAll(killer, definition.loot());
    }
}
