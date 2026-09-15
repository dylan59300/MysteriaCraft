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
 *
 * @param pity si true, cet effet fait partie du pool "gros lot" garanti par le systeme de pity
 *             (voir LuckyBlockManager#pickEffect) apres pity-seuil casses consecutives sans en
 *             obtenir un. false par defaut : la plupart des effets ne comptent pas comme "gros lot".
 */
public record LuckyBlockEffect(
        EffectKind kind,
        double chance,
        String displayName,
        Reward reward,
        BadEffectType badType,
        String stringValue,
        int intValue,
        int amplifier,
        boolean pity
) {
}
