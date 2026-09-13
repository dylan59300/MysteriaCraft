package com.mysteriacraft.crates;

import org.bukkit.Material;

import java.util.List;

/**
 * Definition immuable d'une caisse (crate), chargee depuis crates.yml.
 *
 * @param drawsPerOpen nombre de recompenses tirees a chaque ouverture (1 par defaut).
 * @param keyPrice     prix d'une cle en monnaie interne (0 ou moins = pas achetable).
 * @param pityThreshold nombre d'ouvertures consecutives sans LEGENDAIRE avant qu'un
 *                      legendaire soit garanti au tirage suivant (0 = desactive).
 */
public record Crate(
        String id,
        String displayName,
        Material icon,
        int order,
        List<String> lore,
        String permission,
        CrateAnimationType animation,
        int drawsPerOpen,
        double keyPrice,
        int pityThreshold,
        List<CrateReward> rewards
) {

    public boolean hasPermissionRequirement() {
        return permission != null && !permission.isBlank();
    }

    public boolean isPurchasable() {
        return keyPrice > 0;
    }

    public boolean hasPity() {
        return pityThreshold > 0;
    }

    public double totalWeight() {
        double total = 0;
        for (CrateReward reward : rewards) {
            total += reward.chance();
        }
        return total;
    }
}
