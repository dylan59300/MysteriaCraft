package com.mysteriacraft.crates;

import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardType;
import org.bukkit.inventory.ItemStack;

/**
 * Une recompense possible d'une caisse : une Reward generique (partagee avec BattlePass/Quetes)
 * plus les infos propres au tirage de crate (poids, rarete visuelle).
 *
 * @param chance poids relatif utilise pour le tirage pondere (n'a pas besoin de sommer a 100 ;
 *               la normalisation se fait automatiquement au tirage).
 */
public record CrateReward(String id, Reward reward, double chance, Rarity rarity) {

    // Delegations pratiques pour eviter de reecrire reward.reward().xxx() partout dans le module.
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
