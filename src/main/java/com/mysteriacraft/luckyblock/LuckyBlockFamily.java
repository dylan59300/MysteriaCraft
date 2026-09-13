package com.mysteriacraft.luckyblock;

import org.bukkit.Material;

import java.util.List;

/**
 * Une famille de Lucky Block (ex: commune, rare), chargee depuis luckyblocks.yml.
 * Chaque famille a son propre pool d'effets et son propre cooldown.
 */
public record LuckyBlockFamily(
        String id,
        String displayName,
        Material blockMaterial,
        int order,
        long cooldownSeconds,
        double buyPrice,
        List<RecipeIngredient> recipe,
        List<LuckyBlockEffect> effects
) {

    public boolean isPurchasable() {
        return buyPrice > 0;
    }

    public double totalWeight() {
        double total = 0;
        for (LuckyBlockEffect effect : effects) {
            total += effect.chance();
        }
        return total;
    }
}
