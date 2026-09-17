package com.mysteriacraft.admin.listeners;

import com.mysteriacraft.admin.AdminMaintenanceManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Map;

/**
 * Bloque l'usage d'une commande dont le module a ete mis en maintenance (voir
 * AdminMaintenanceManager), pour tous sauf les admins (permission mysteriacraft.admin).
 */
public class AdminMaintenanceListener implements Listener {

    private final AdminMaintenanceManager maintenanceManager;
    private final MessageManager messages;

    public AdminMaintenanceListener(AdminMaintenanceManager maintenanceManager, MessageManager messages) {
        this.maintenanceManager = maintenanceManager;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission("mysteriacraft.admin")) {
            return;
        }
        String saisie = event.getMessage().substring(1);
        String commande = saisie.split(" ", 2)[0].toLowerCase();
        if (!maintenanceManager.isDisabled(commande)) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "admin.maintenance-bloque", Map.of("commande", commande));
    }
}
