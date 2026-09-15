package com.mysteriacraft.quests;

import com.mysteriacraft.core.SeasonalWindow;
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
 * @param actifDu date de debut (format "MM-jj") d'une quete SAISONNIERE (Halloween...), null pour
 *                une quete permanente. Doit etre defini AVEC actifAu.
 * @param actifAu date de fin (format "MM-jj") d'une quete saisonniere, ou null.
 * @param meteo condition meteo optionnelle (PLUIE ou CLAIR) : la quete ne progresse que si le
 *              temps du monde du joueur correspond au moment de l'action. Null = pas de condition.
 * @param reveleeApres id d'une AUTRE quete qui doit etre terminee cette meme periode pour que
 *                     celle-ci apparaisse dans /quests (quete "secrete"). Null = toujours visible.
 *                     Continue de progresser silencieusement meme non revelee.
 * @param contrat si vrai, la quete ne progresse qu'apres acceptation explicite via
 *                "/quests accepter" (quete optionnelle plus difficile, recompense plus genereuse).
 * @param lore ligne de description narrative optionnelle affichee dans /quests. Null = aucune.
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
        QuestPeriod period,
        String actifDu,
        String actifAu,
        WeatherCondition meteo,
        String reveleeApres,
        boolean contrat,
        String lore
) {

    public boolean isActiveNow() {
        return SeasonalWindow.isActiveNow(actifDu, actifAu);
    }

    /** Condition meteo optionnelle d'une quete (voir champ "meteo" dans quests.yml). */
    public enum WeatherCondition {
        PLUIE,
        CLAIR;

        public static WeatherCondition fromString(String value) {
            if (value == null) {
                return null;
            }
            try {
                return WeatherCondition.valueOf(value.trim().toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }
}
