package com.mysteriacraft.customitems;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Definition immuable d'un minerai/objet custom, charge depuis custom_items.yml.
 * Rendu visuel via son item-de-base vanilla (pas de resource pack requis).
 *
 * @param sourceOres blocs qui peuvent faire apparaitre cet item a la casse (liste vide = aucune
 *                   source naturelle, obtenable uniquement en recompense : crate/battlepass/quete/luckyblock).
 * @param dropChance chance en % de recevoir l'item a chaque casse d'un bloc source.
 * @param recipeShape forme de la recette (1 a 3 lignes de 1 a 3 caracteres, espace = case vide),
 *                     vide si l'item n'est pas craftable a l'etabli.
 * @param recipeIngredients association caractere de la forme -> materiau vanilla requis.
 * @param crateAuto si true, ajoute automatiquement cet item au tirage de toutes les caisses
 *                  (module Crates), y compris les caisses ajoutees plus tard, sans avoir besoin
 *                  de l'ajouter a la main dans crates.yml. Voir CrateManager#syncAutoCustomItemRewards.
 * @param crateChance poids relatif utilise pour le tirage pondere quand crateAuto est actif.
 * @param crateRarity rarete visuelle (texte brut : COMMUNE/RARE/EPIQUE/LEGENDAIRE, parse par
 *                    crates.Rarity) affichee pour cet item quand il est tire en caisse.
 * @param crateQuantity quantite donnee quand cet item est tire en caisse.
 */
public record CustomItemDefinition(
        String id,
        String displayName,
        List<String> lore,
        Material baseItem,
        List<Material> sourceOres,
        double dropChance,
        double sellPrice,
        List<String> recipeShape,
        Map<Character, Material> recipeIngredients,
        boolean crateAuto,
        double crateChance,
        String crateRarity,
        int crateQuantity
) {

    public boolean hasNaturalSource() {
        return !sourceOres.isEmpty();
    }

    public boolean isSellable() {
        return sellPrice > 0;
    }

    public boolean isCraftable() {
        return !recipeShape.isEmpty();
    }
}
