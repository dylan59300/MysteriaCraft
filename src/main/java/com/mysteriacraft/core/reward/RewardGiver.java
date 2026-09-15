package com.mysteriacraft.core.reward;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Distribue une Reward a un joueur, quel que soit son type (objet, economie, boost xp...).
 * Instance partagee entre les modules BattlePass et Quetes pour eviter de dupliquer cette
 * logique dans chacun.
 *
 * BattlePassService est branche apres coup via setBattlePassService() pour casser la dependance
 * circulaire (BattlePassService a lui-meme besoin d'un RewardGiver pour honorer ses paliers).
 */
public class RewardGiver {

    private final EconomyManager economyManager;
    private final MessageManager messages;

    private XpBoosterHandler boosterHandler;
    private PetUnlockHandler petUnlockHandler;
    private LuckyBlockGiveHandler luckyBlockGiveHandler;
    private CustomItemGiveHandler customItemGiveHandler;
    private GeneratorGiveHandler generatorGiveHandler;
    private MachineGiveHandler machineGiveHandler;
    private IslandUpgradeGiveHandler islandUpgradeGiveHandler;
    private TitleUnlockHandler titleUnlockHandler;

    public RewardGiver(EconomyManager economyManager, MessageManager messages) {
        this.economyManager = economyManager;
        this.messages = messages;
    }

    /** Petit contrat minimal pour activer un boost xp, implemente par BattlePassService (branche apres coup). */
    public interface XpBoosterHandler {
        void activateBooster(Player player, long durationSeconds, double multiplier);
    }

    /** Petit contrat minimal pour debloquer un pet, implemente par PetService (branche apres coup). */
    public interface PetUnlockHandler {
        void unlockFromReward(Player player, String petId);
    }

    /** Petit contrat minimal pour donner un item Lucky Block marque, implemente par LuckyBlockManager. */
    public interface LuckyBlockGiveHandler {
        void giveLuckyBlock(Player player, String familyId);
    }

    /** Petit contrat minimal pour donner un item custom, implemente par CustomItemService. */
    public interface CustomItemGiveHandler {
        void giveCustomItem(Player player, String customItemId, int amount);
    }

    /** Petit contrat minimal pour donner un Generateur d'Argent, implemente par GeneratorService. */
    public interface GeneratorGiveHandler {
        void giveGenerator(Player player, String generatorTypeId, int amount);
    }

    /** Petit contrat minimal pour donner une Machine ("transformation" ou "miniere"), implemente
     * via un petit adaptateur branche dans MysteriaCraft (MachineManager/MiningMachineManager
     * n'ont pas de type commun, l'adaptateur choisit lequel appeler selon machineId). */
    public interface MachineGiveHandler {
        void giveMachine(Player player, String machineId, int amount);
    }

    /** Petit contrat minimal pour agrandir gratuitement l'ile du joueur, implemente par IslandService. */
    public interface IslandUpgradeGiveHandler {
        void giveFreeIslandUpgrade(Player player, int blocks);
    }

    /** Petit contrat minimal pour debloquer un titre de chat, implemente par BattlePassService. */
    public interface TitleUnlockHandler {
        void unlockTitle(Player player, String titre);
    }

    public void setBoosterHandler(XpBoosterHandler boosterHandler) {
        this.boosterHandler = boosterHandler;
    }

    public void setPetUnlockHandler(PetUnlockHandler petUnlockHandler) {
        this.petUnlockHandler = petUnlockHandler;
    }

    public void setLuckyBlockGiveHandler(LuckyBlockGiveHandler luckyBlockGiveHandler) {
        this.luckyBlockGiveHandler = luckyBlockGiveHandler;
    }

    public void setCustomItemGiveHandler(CustomItemGiveHandler customItemGiveHandler) {
        this.customItemGiveHandler = customItemGiveHandler;
    }

    public void setGeneratorGiveHandler(GeneratorGiveHandler generatorGiveHandler) {
        this.generatorGiveHandler = generatorGiveHandler;
    }

    public void setMachineGiveHandler(MachineGiveHandler machineGiveHandler) {
        this.machineGiveHandler = machineGiveHandler;
    }

    public void setIslandUpgradeGiveHandler(IslandUpgradeGiveHandler islandUpgradeGiveHandler) {
        this.islandUpgradeGiveHandler = islandUpgradeGiveHandler;
    }

    public void setTitleUnlockHandler(TitleUnlockHandler titleUnlockHandler) {
        this.titleUnlockHandler = titleUnlockHandler;
    }

    public void give(Player player, Reward reward) {
        switch (reward.type()) {
            case ECONOMIE -> economyManager.deposit(player.getUniqueId(), reward.economyAmount());
            case BOOST_XP -> {
                if (boosterHandler != null) {
                    boosterHandler.activateBooster(player, reward.boosterDurationSeconds(), reward.boosterMultiplier());
                }
            }
            case PET -> {
                if (petUnlockHandler != null) {
                    petUnlockHandler.unlockFromReward(player, reward.petId());
                }
            }
            case LUCKYBLOCK -> {
                if (luckyBlockGiveHandler != null) {
                    luckyBlockGiveHandler.giveLuckyBlock(player, reward.luckyBlockFamilyId());
                }
            }
            case OBJET_CUSTOM -> {
                if (customItemGiveHandler != null) {
                    customItemGiveHandler.giveCustomItem(player, reward.customItemId(), Math.max(1, reward.amount()));
                }
            }
            case GENERATEUR -> {
                if (generatorGiveHandler != null) {
                    generatorGiveHandler.giveGenerator(player, reward.customItemId(), Math.max(1, reward.amount()));
                }
            }
            case MACHINE -> {
                if (machineGiveHandler != null) {
                    machineGiveHandler.giveMachine(player, reward.customItemId(), Math.max(1, reward.amount()));
                }
            }
            case AGRANDISSEMENT_ILE -> {
                if (islandUpgradeGiveHandler != null) {
                    islandUpgradeGiveHandler.giveFreeIslandUpgrade(player, Math.max(1, reward.amount()));
                }
            }
            case TITRE_CHAT -> {
                if (titleUnlockHandler != null) {
                    titleUnlockHandler.unlockTitle(player, reward.customItemId());
                }
            }
            case ITEM -> giveItem(player, reward);
        }
    }

    public void giveAll(Player player, List<Reward> rewards) {
        for (Reward reward : rewards) {
            give(player, reward);
        }
    }

    private void giveItem(Player player, Reward reward) {
        if (reward.item() == null) {
            return;
        }
        ItemStack toGive = reward.item().clone();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(toGive);
        if (!leftovers.isEmpty()) {
            Location dropLocation = player.getLocation();
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(dropLocation, leftover);
            }
            messages.send(player, "general.inventaire-plein");
        }
    }
}
