package com.mysteriacraft.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * InventoryHolder marqueur permettant au MenuListener de retrouver le Menu associe
 * a un inventaire ouvert, afin de lui deleguer les clics.
 */
public class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private Inventory inventory;

    public MenuHolder(Menu menu) {
        this.menu = menu;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Menu getMenu() {
        return menu;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
