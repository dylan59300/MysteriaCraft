package com.mysteriacraft.marchand;

import com.mysteriacraft.core.reward.Reward;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Un rachat propose par un PNJ Marchand : le SENS INVERSE d'une offre normale (voir
 * MarchandOffer) : le joueur donne "quantite" exemplaires de "materiel", et recoit en echange
 * une recompense (typiquement des pieces d'echange via OBJET_CUSTOM, mais n'importe quel type de
 * Reward fonctionne), pour ecouler un surplus de ressources (ex: lingots de generateurs).
 */
public record MarchandRachat(
        String id,
        Material materiel,
        int quantite,
        MarchandOffer.LimitePeriode limitePeriode,
        int limiteQuantite,
        Reward recompense,
        String displayName,
        ItemStack icon
) {

    public boolean isLimited() {
        return limitePeriode != MarchandOffer.LimitePeriode.NONE && limiteQuantite > 0;
    }
}
