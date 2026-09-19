package com.mysteriacraft.storage;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Marqueur permettant a StorageListener d'identifier un inventaire de stockage personnel
 * (sac/coffre-fort) ouvert par un joueur, afin de persister son contenu a la fermeture. */
public class StorageHolder implements InventoryHolder {

    private final UUID uuid;
    private final String type;
    private Inventory inventory;

    public StorageHolder(UUID uuid, String type) {
        this.uuid = uuid;
        this.type = type;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getType() {
        return type;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
