package com.mysteriacraft.core.reward;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.crates.CrateManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

/**
 * Distribue une Reward a un joueur, quel que soit son type (objet, economie, cle de caisse,
 * boost xp). Instance partagee entre les modules Crates, BattlePass et Quetes pour eviter de
 * dupliquer cette logique dans chacun.
 *
 * BattlePassService est branche apres coup via setBattlePassService() pour casser la dependance
 * circulaire (BattlePassService a lui-meme besoin d'un RewardGiver pour honorer ses paliers).
 */
public class RewardGiver {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final CrateManager crateManager;
    private final MessageManager messages;

    private XpBoosterHandler boosterHandler;

    public RewardGiver(Plugin plugin, EconomyManager economyManager, CrateManager crateManager, MessageManager messages) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.crateManager = crateManager;
        this.messages = messages;
    }

    /** Petit contrat minimal pour activer un boost xp, implemente par BattlePassService (branche apres coup). */
    public interface XpBoosterHandler {
        void activateBooster(Player player, long durationSeconds, double multiplier);
    }

    public void setBoosterHandler(XpBoosterHandler boosterHandler) {
        this.boosterHandler = boosterHandler;
    }

    public void give(Player player, Reward reward) {
        switch (reward.type()) {
            case ECONOMIE -> economyManager.deposit(player.getUniqueId(), reward.economyAmount());
            case CLE_CAISSE -> Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> crateManager.addKeys(player.getUniqueId(), reward.crateId(), reward.crateKeyAmount()));
            case BOOST_XP -> {
                if (boosterHandler != null) {
                    boosterHandler.activateBooster(player, reward.boosterDurationSeconds(), reward.boosterMultiplier());
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
            messages.send(player, "kits.inventaire-plein");
        }
    }
}
