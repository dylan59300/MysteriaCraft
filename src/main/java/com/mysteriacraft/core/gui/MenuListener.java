package com.mysteriacraft.core.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Listener unique pour tous les menus GUI du plugin : intercepte les clics/fermetures
 * et les redirige vers le Menu associe a l'inventaire concerne.
 */
public class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MenuHolder menuHolder)) {
            return;
        }
        event.setCancelled(true);
        menuHolder.getMenu().handleClick(event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MenuHolder menuHolder) {
            menuHolder.getMenu().handleClose();
        }
    }
}
