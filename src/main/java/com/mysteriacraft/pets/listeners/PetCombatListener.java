package com.mysteriacraft.pets.listeners;

import com.mysteriacraft.pets.PetService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Chance d'esquive (voir PetDefinition#esquivePourcent) : annule completement les degats subis
 * par un joueur ayant un pet de combat actif, selon le pourcentage configure de ce pet.
 */
public class PetCombatListener implements Listener {

    private final PetService petService;

    public PetCombatListener(PetService petService) {
        this.petService = petService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double esquive = petService.getEsquivePourcent(player.getUniqueId());
        if (esquive <= 0) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble(100) < esquive) {
            event.setCancelled(true);
        }
    }
}
