package com.mysteriacraft.crates;

import org.bukkit.Material;

import java.util.List;

/**
 * Definition immuable d'une caisse (crate), chargee depuis crates.yml.
 */
public record Crate(
        String id,
        String displayName,
        Material icon,
        int order,
        List<String> lore,
        String permission,
        CrateAnimationType animation,
        List<CrateReward> rewards
) {

    public boolean hasPermissionRequirement() {
        return permission != null && !permission.isBlank();
    }

    public double totalWeight() {
        double total = 0;
        for (CrateReward reward : rewards) {
            total += reward.chance();
        }
        return total;
    }
}
