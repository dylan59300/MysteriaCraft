package com.mysteriacraft.core.reward;

import org.bukkit.inventory.ItemStack;

/**
 * Recompense generique, partagee entre BattlePass et Quetes pour eviter de dupliquer
 * cette structure et sa logique de distribution dans chaque module.
 * Selon le type, seuls certains champs sont pertinents (les autres restent a leur valeur par defaut) :
 * - ITEM : item
 * - ECONOMIE : economyAmount
 * - BOOST_XP : boosterDurationSeconds, boosterMultiplier
 * - PET : petId
 * - LUCKYBLOCK : luckyBlockFamilyId
 * - OBJET_CUSTOM : customItemId + amount
 * - GENERATEUR : customItemId reutilise comme id de type de generateur + amount
 * - GENERATEUR_LUCKYBLOCK : luckyBlockFamilyId reutilise comme famille ciblee + amount
 * - MACHINE : customItemId reutilise comme id de machine ("transformation" ou "miniere") + amount
 * - AGRANDISSEMENT_ILE : amount reutilise comme nombre de blocs de rayon offerts
 * - TITRE_CHAT : customItemId reutilise comme texte du titre debloque
 * - COMMANDE : customItemId reutilise comme commande a executer (console), avec {joueur} remplace
 *              par le nom du joueur au moment de la donner
 */
public record Reward(
        RewardType type,
        ItemStack item,
        double economyAmount,
        int amount,
        long boosterDurationSeconds,
        double boosterMultiplier,
        String petId,
        String luckyBlockFamilyId,
        String customItemId,
        String displayName,
        ItemStack displayIcon
) {

    public static Reward ofItem(ItemStack item, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.ITEM, item, 0, 0, 0, 0, null, null, null, displayName, displayIcon);
    }

    public static Reward ofEconomy(double amount, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.ECONOMIE, null, amount, 0, 0, 0, null, null, null, displayName, displayIcon);
    }

    public static Reward ofXpBooster(long durationSeconds, double multiplier, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.BOOST_XP, null, 0, 0, durationSeconds, multiplier, null, null, null, displayName, displayIcon);
    }

    public static Reward ofPet(String petId, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.PET, null, 0, 0, 0, 0, petId, null, null, displayName, displayIcon);
    }

    public static Reward ofLuckyBlock(String familyId, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.LUCKYBLOCK, null, 0, 0, 0, 0, null, familyId, null, displayName, displayIcon);
    }

    public static Reward ofCustomItem(String customItemId, int amount, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.OBJET_CUSTOM, null, 0, amount, 0, 0, null, null, customItemId, displayName, displayIcon);
    }

    public static Reward ofGenerator(String generatorTypeId, int amount, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.GENERATEUR, null, 0, amount, 0, 0, null, null, generatorTypeId, displayName, displayIcon);
    }

    public static Reward ofGeneratorLuckyBlock(String familyId, int amount, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.GENERATEUR_LUCKYBLOCK, null, 0, amount, 0, 0, null, familyId, null, displayName, displayIcon);
    }

    public static Reward ofMachine(String machineId, int amount, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.MACHINE, null, 0, amount, 0, 0, null, null, machineId, displayName, displayIcon);
    }

    public static Reward ofIslandUpgrade(int blocks, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.AGRANDISSEMENT_ILE, null, 0, blocks, 0, 0, null, null, null, displayName, displayIcon);
    }

    public static Reward ofTitle(String titre, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.TITRE_CHAT, null, 0, 0, 0, 0, null, null, titre, displayName, displayIcon);
    }

    public static Reward ofCommand(String commande, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.COMMANDE, null, 0, 0, 0, 0, null, null, commande, displayName, displayIcon);
    }

    /** Copie cette recompense avec sa "valeur" doublee (montant/quantite selon le type), utilisee
     * par le boost de quete achetable (voir QuestService). Les types sans notion de quantite
     * (PET, LUCKYBLOCK) sont renvoyes inchanges. */
    public Reward doubled() {
        return switch (type) {
            case ECONOMIE -> new Reward(type, item, economyAmount * 2, amount, boosterDurationSeconds, boosterMultiplier,
                    petId, luckyBlockFamilyId, customItemId, displayName, displayIcon);
            case ITEM -> {
                ItemStack doubledItem = item.clone();
                doubledItem.setAmount(doubledItem.getAmount() * 2);
                yield new Reward(type, doubledItem, economyAmount, amount, boosterDurationSeconds, boosterMultiplier,
                        petId, luckyBlockFamilyId, customItemId, displayName, displayIcon);
            }
            case OBJET_CUSTOM, GENERATEUR, GENERATEUR_LUCKYBLOCK, MACHINE, AGRANDISSEMENT_ILE -> new Reward(type, item,
                    economyAmount, amount * 2, boosterDurationSeconds, boosterMultiplier,
                    petId, luckyBlockFamilyId, customItemId, displayName, displayIcon);
            case BOOST_XP -> new Reward(type, item, economyAmount, amount, boosterDurationSeconds * 2, boosterMultiplier,
                    petId, luckyBlockFamilyId, customItemId, displayName, displayIcon);
            default -> this;
        };
    }
}
