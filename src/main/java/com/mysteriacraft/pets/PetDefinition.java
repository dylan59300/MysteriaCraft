package com.mysteriacraft.pets;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * Definition immuable d'un pet (vrai mob Bukkit apprivoise), chargee depuis pets.yml.
 */
public record PetDefinition(
        String id,
        String displayName,
        EntityType entityType,
        Material icon,
        int order,
        List<String> lore,
        double price,
        String permission,
        double degatsBonus,
        double esquivePourcent
) {

    public boolean hasPermissionRequirement() {
        return permission != null && !permission.isBlank();
    }

    public boolean isPurchasable() {
        return price > 0;
    }
}
