package com.mysteriacraft.battlepass;

import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardType;
import org.bukkit.inventory.ItemStack;

/**
 * Une recompense de palier de BattlePass (piste gratuite ou premium), enveloppant la Reward
 * generique partagee avec Quetes.
 */
public record BattlePassReward(Reward reward) {

    public RewardType type() {
        return reward.type();
    }

    public ItemStack item() {
        return reward.item();
    }

    public double economyAmount() {
        return reward.economyAmount();
    }

    public String displayName() {
        return reward.displayName();
    }

    public ItemStack displayIcon() {
        return reward.displayIcon();
    }
}
