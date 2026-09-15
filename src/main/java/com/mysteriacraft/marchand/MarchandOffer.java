package com.mysteriacraft.marchand;

import com.mysteriacraft.core.reward.Reward;
import org.bukkit.inventory.ItemStack;

/**
 * Une offre du PNJ Marchand : coute "cout" pieces d'echange (voir MarchandManager#getPieceItemId)
 * contre la recompense donnee (n'importe quel type de Reward, voir marchand.yml).
 */
public record MarchandOffer(
        String id,
        int cout,
        Reward recompense,
        String displayName,
        ItemStack icon
) {
}
