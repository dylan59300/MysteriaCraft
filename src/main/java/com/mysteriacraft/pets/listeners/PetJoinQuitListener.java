package com.mysteriacraft.pets.listeners;

import com.mysteriacraft.pets.PetService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Re-invoque le pet actif d'un joueur a sa connexion, et retire l'entite (sans oublier le choix)
 * a sa deconnexion pour eviter les mobs orphelins.
 */
public class PetJoinQuitListener implements Listener {

    private final PetService petService;

    public PetJoinQuitListener(PetService petService) {
        this.petService = petService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        petService.respawnSavedPet(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        petService.onPlayerQuit(event.getPlayer());
    }
}
