package com.mysteriacraft.customitems;

import org.bukkit.Material;

import java.util.List;

/**
 * Definition immuable d'un minerai/objet custom, charge depuis custom_items.yml.
 * Rendu visuel via CustomModelData sur un item de base (voir resourcepack/README.md).
 *
 * @param sourceOres blocs qui peuvent faire apparaitre cet item a la casse (liste vide = aucune
 *                   source naturelle, obtenable uniquement en recompense : crate/battlepass/quete/luckyblock).
 * @param dropChance chance en % de recevoir l'item a chaque casse d'un bloc source.
 */
public record CustomItemDefinition(
        String id,
        String displayName,
        List<String> lore,
        Material baseItem,
        int customModelData,
        List<Material> sourceOres,
        double dropChance,
        double sellPrice
) {

    public boolean hasNaturalSource() {
        return !sourceOres.isEmpty();
    }

    public boolean isSellable() {
        return sellPrice > 0;
    }
}
