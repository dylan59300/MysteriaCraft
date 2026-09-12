package com.mysteriacraft.kits;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Definition immuable d'un kit, chargee depuis kits.yml.
 */
public record Kit(
        String id,
        String displayName,
        Material icon,
        int order,
        List<String> lore,
        String permission,
        long cooldownSeconds,
        List<ItemStack> items
) {

    public boolean hasPermissionRequirement() {
        return permission != null && !permission.isBlank();
    }
}
