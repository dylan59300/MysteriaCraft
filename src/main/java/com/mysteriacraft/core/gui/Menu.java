package com.mysteriacraft.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * Base commune pour tous les menus GUI du plugin (kits, crates, battlepass, quetes, pets...).
 * Chaque menu sait construire son inventaire et reagir aux clics dedans.
 */
public abstract class Menu {

    protected final Player viewer;

    protected Menu(Player viewer) {
        this.viewer = viewer;
    }

    /** Construit l'inventaire a afficher (appele a chaque ouverture, pour des donnees toujours a jour). */
    public abstract Inventory build();

    /** Appele quand le joueur clique dans l'inventaire de ce menu. L'evenement est deja annule par le listener. */
    public abstract void handleClick(InventoryClickEvent event);

    /** Appele quand le joueur ferme l'inventaire de ce menu (optionnel a surcharger). */
    public void handleClose() {
    }

    public void open() {
        viewer.openInventory(build());
    }
}
