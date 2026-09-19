package com.mysteriacraft.storage.listeners;

import com.mysteriacraft.storage.StorageHolder;
import com.mysteriacraft.storage.StorageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

/** Persiste le contenu d'un Sac/Coffre-fort des que le joueur ferme son inventaire. */
public class StorageListener implements Listener {

    private final StorageService service;

    public StorageListener(StorageService service) {
        this.service = service;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof StorageHolder storageHolder) {
            service.handleClose(storageHolder);
        }
    }
}
