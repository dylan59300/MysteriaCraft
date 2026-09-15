package com.mysteriacraft.battlepass;

import java.util.List;

/**
 * Un palier du BattlePass : XP cumulee necessaire pour l'atteindre, et ses recompenses
 * (gratuite et/ou premium, l'une des deux pouvant etre absente).
 *
 * @param mysteryPool si non vide, la recompense gratuite EFFECTIVE de ce palier est tiree au
 *                    hasard dans cette liste (une seule fois par joueur, roule et persistee a la
 *                    premiere reclamation, voir BattlePassManager#resolveMysteryReward). freeReward
 *                    reste alors null : le pool remplace entierement la recompense gratuite statique.
 * @param chapitre numero de chapitre optionnel (0 = aucun) regroupant plusieurs paliers sous un
 *                 theme commun (voir section "chapitres" dans battlepass.yml).
 */
public record BattlePassLevel(
        int level,
        long xpRequired,
        BattlePassReward freeReward,
        BattlePassReward premiumReward,
        List<BattlePassReward> mysteryPool,
        int chapitre
) {
}
