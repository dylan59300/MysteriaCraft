package com.mysteriacraft.battlepass;

import org.bukkit.inventory.ItemStack;

/**
 * Une recompense de palier de BattlePass (piste gratuite ou premium).
 */
public record BattlePassReward(
        RewardType type,
        ItemStack item,
        double economyAmount,
        String displayName,
        ItemStack displayIcon
) {
}
