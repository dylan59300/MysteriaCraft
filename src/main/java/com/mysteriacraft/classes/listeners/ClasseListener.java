package com.mysteriacraft.classes.listeners;

import com.mysteriacraft.classes.ClasseService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Reapplique l'effet de la classe active a la connexion et apres chaque mort (qui efface tous
 * les effets de potion).
 */
public class ClasseListener implements Listener {

    private final ClasseService service;

    public ClasseListener(ClasseService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.applyActiveClassEffect(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        service.applyActiveClassEffect(event.getPlayer());
    }
}
