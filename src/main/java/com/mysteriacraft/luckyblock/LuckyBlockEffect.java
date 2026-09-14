package com.mysteriacraft.luckyblock;

import com.mysteriacraft.core.reward.Reward;

/**
 * Un effet possible d'un Lucky Block, tire au poids ("chance") parmi tous les effets
 * (bons et mauvais) d'une meme famille.
 *
 * Selon kind, seuls certains champs sont pertinents :
 * - BON : reward (recompense donnee via RewardGiver, meme systeme que BattlePass/Quetes)
 * - MAUVAIS (badType == TNT) : intValue = nombre de TNT
 * - MAUVAIS (badType == MOBS) : stringValue = type d'entite, intValue = nombre de mobs
 * - MAUVAIS (badType == POTION) : stringValue = type d'effet, intValue = duree (ticks), amplifier
 * - MAUVAIS (badType == FOUDRE) : aucun champ supplementaire
 */
public record LuckyBlockEffect(
        EffectKind kind,
        double chance,
        String displayName,
        Reward reward,
        BadEffectType badType,
        String stringValue,
        int intValue,
        int amplifier
) {
}
