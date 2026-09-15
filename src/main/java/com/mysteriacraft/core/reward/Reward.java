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
 * - MACHINE : customItemId reutilise comme id de machine ("transformation" ou "miniere") + amount
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

    public static Reward ofMachine(String machineId, int amount, String displayName, ItemStack displayIcon) {
        return new Reward(RewardType.MACHINE, null, 0, amount, 0, 0, null, null, machineId, displayName, displayIcon);
    }
}
