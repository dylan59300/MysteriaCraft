package com.mysteriacraft.battlepass;

/**
 * Un palier du BattlePass : XP cumulee necessaire pour l'atteindre, et ses recompenses
 * (gratuite et/ou premium, l'une des deux pouvant etre absente).
 */
public record BattlePassLevel(
        int level,
        long xpRequired,
        BattlePassReward freeReward,
        BattlePassReward premiumReward
) {
}
