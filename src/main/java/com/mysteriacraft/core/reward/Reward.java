package com.mysteriacraft.core.reward;

import org.bukkit.inventory.ItemStack;

/**
 * Recompense generique (objet ou credit d'economie), partagee entre Crates, BattlePass et Quetes
 * pour eviter de dupliquer cette structure et sa logique de distribution dans chaque module.
 */
public record Reward(
        RewardType type,
        ItemStack item,
        double economyAmount,
        String displayName,
        ItemStack displayIcon
) {

    public static Reward ofItem(ItemStack item, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.ITEM, item, 0, displayName, displayIcon);
    }

    public static Reward ofEconomy(double amount, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.ECONOMIE, null, amount, displayName, displayIcon);
    }
}
