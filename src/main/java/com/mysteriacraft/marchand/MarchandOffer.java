package com.mysteriacraft.marchand;

import com.mysteriacraft.core.reward.Reward;
import org.bukkit.inventory.ItemStack;

/**
 * Une offre d'un PNJ Marchand : coute "cout" pieces d'echange (avant reduction fidelite, voir
 * MarchandDefinition) contre la recompense donnee (n'importe quel type de Reward).
 *
 * @param limitePeriode NONE = illimite, sinon nombre max d'achats de cette offre par joueur et
 *                      par periode (voir limiteQuantite).
 */
public record MarchandOffer(
        String id,
        int cout,
        LimitePeriode limitePeriode,
        int limiteQuantite,
        Reward recompense,
        String displayName,
        ItemStack icon
) {

    public enum LimitePeriode {
        NONE,
        JOUR,
        SEMAINE;

        public static LimitePeriode fromString(String value) {
            if (value == null) {
                return NONE;
            }
            try {
                return LimitePeriode.valueOf(value.trim().toUpperCase());
            } catch (Exception e) {
                return NONE;
            }
        }
    }

    public boolean isLimited() {
        return limitePeriode != LimitePeriode.NONE && limiteQuantite > 0;
    }
}
