package com.mysteriacraft.quests;

import com.mysteriacraft.core.reward.Reward;
import org.bukkit.inventory.ItemStack;

/**
 * Definition immuable d'une quete (quotidienne ou hebdomadaire), chargee depuis quests.yml.
 *
 * @param target materiau (BREAK_BLOCK/PLACE_BLOCK) ou type d'entite (KILL_MOB) requis ; null = tout objet/mob compte (ex: FISH).
 * @param objective quantite a atteindre pour terminer la quete.
 * @param xpReward xp de BattlePass creditee automatiquement a la completion.
 * @param reward recompense directe optionnelle (objet ou economie), donnee automatiquement a la completion.
 * @param icon icone affichee dans le menu /quests.
 */
public record QuestDefinition(
        String id,
        String displayName,
        ItemStack icon,
        QuestType type,
        String target,
        int objective,
        long xpReward,
        Reward reward,
        QuestPeriod period
) {
}
