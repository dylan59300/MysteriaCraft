package com.mysteriacraft.runes.listeners;

import com.mysteriacraft.runes.RuneService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/** Declenche l'effet d'une rune gravee des qu'un joueur touche une cible vivante avec son arme. */
public class RuneListener implements Listener {

    private final RuneService service;

    public RuneListener(RuneService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) {
            return;
        }
        service.handleHit(attacker, weapon, target);
    }
}
