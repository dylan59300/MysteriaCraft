package com.mysteriacraft.crates;

import org.bukkit.inventory.ItemStack;

/**
 * Une recompense possible d'une caisse, avec son poids (chance relative) et sa rarete visuelle.
 *
 * @param chance   poids relatif utilise pour le tirage pondere (n'a pas besoin de sommer a 100 ;
 *                 la normalisation se fait automatiquement au tirage).
 * @param item     l'ItemStack donne si type == ITEM (null sinon).
 * @param economyAmount le montant credite si type == ECONOMIE (ignore sinon).
 * @param displayIcon l'icone montree dans les menus/animations (toujours renseignee).
 */
public record CrateReward(
        String id,
        RewardType type,
        ItemStack item,
        double economyAmount,
        double chance,
        Rarity rarity,
        String displayName,
        ItemStack displayIcon
) {
}
