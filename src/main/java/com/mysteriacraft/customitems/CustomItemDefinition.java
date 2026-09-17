package com.mysteriacraft.customitems;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Map;

/**
 * Definition immuable d'un minerai/objet custom, charge depuis custom_items.yml.
 * Rendu visuel via son item-de-base vanilla (pas de resource pack requis).
 *
 * @param sourceOres blocs qui peuvent faire apparaitre cet item a la casse (liste vide = aucune
 *                   source naturelle, obtenable uniquement en recompense : battlepass/quete/luckyblock).
 * @param dropChance chance en % de recevoir l'item a chaque casse d'un bloc source.
 * @param recipeShape forme de la recette (1 a 3 lignes de 1 a 3 caracteres, espace = case vide),
 *                     vide si l'item n'est pas craftable a l'etabli.
 * @param recipeIngredients association caractere de la forme -> materiau vanilla requis.
 * @param enchantments enchantements appliques (sans limite de niveau vanilla) pour un equipement
 *                      "full custom" (armes/outils/armures) : id vanilla -> niveau. Vide = aucun.
 * @param extraAttackDamage bonus de degats d'attaque (attribut GENERIC_ATTACK_DAMAGE) ajoute EN
 *                          PLUS des degats de base de l'item-de-base. 0 = aucun bonus.
 * @param extraArmor bonus d'armure (attribut GENERIC_ARMOR) ajoute pour une piece d'armure
 *                    (casque/plastron/jambieres/bottes). 0 = aucun bonus.
 * @param unbreakable si true, l'item ne perd jamais de durabilite (indicateur Unbreakable vanilla).
 * @param volDeVie enchantement custom "Vol de vie" : % des degats infliges (arme en main) rendus
 *                 en soin a l'attaquant. 0 = desactive. Ignore si unbreakable est aussi actif sur
 *                 une arme (pas de sens a combiner les deux, mais aucun conflit technique).
 * @param durabiliteCustom nombre d'utilisations (coups portes pour une arme, blocs casses pour un
 *                          outil) avant que l'item ne se brise et disparaisse. 0 = desactive
 *                          (durabilite vanilla normale uniquement). Reparable via l'item custom
 *                          "kit_reparation" (voir CustomItemService/CustomItemListener).
 * @param excluLootMachine si true, exclu du tirage aleatoire "item custom au hasard" de la
 *                          Machine a Transformation (voir MachineService#pickRandomCustomItem) :
 *                          reserve aux items qui ne doivent PAS sortir du RNG (ex: l'oeuf du PNJ
 *                          Marchand, deliberement uniquement craftable/en recompense ciblee).
 * @param customModelData valeur CustomModelData appliquee a l'item (0 = aucune), utilisee par un
 *                         resource pack pour lui donner une texture propre (voir
 *                         "resource pack Platinum" et les modeles vanilla surcharges correspondants).
 *                         Sans resource pack installe cote client, l'item garde juste l'apparence
 *                         de son item-de-base.
 * @param raffineVersId id d'un AUTRE item custom obtenu en passant celui-ci a la Raffinerie (voir
 *                       RaffinerieManager), typiquement un minerai brut custom -> son lingot custom.
 *                       null = pas de conversion (comportement par defaut, inchange).
 * @param recipeCustomIngredients association caractere de la forme -> id d'un AUTRE item custom
 *                                requis dans la recette (en plus/a la place de recipeIngredients,
 *                                qui ne peut referencer que des materiaux vanilla). Vide = aucun.
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
        Map<Enchantment, Integer> enchantments,
        double extraAttackDamage,
        double extraArmor,
        boolean unbreakable,
        double volDeVie,
        int durabiliteCustom,
        boolean excluLootMachine,
        int customModelData,
        String raffineVersId,
        Map<Character, String> recipeCustomIngredients
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

    public boolean isGear() {
        return extraAttackDamage > 0 || extraArmor > 0 || !enchantments.isEmpty();
    }

    public boolean hasCustomDurability() {
        return durabiliteCustom > 0;
    }

    public boolean hasCustomModelData() {
        return customModelData > 0;
    }

    public boolean hasRaffinage() {
        return raffineVersId != null;
    }
}
