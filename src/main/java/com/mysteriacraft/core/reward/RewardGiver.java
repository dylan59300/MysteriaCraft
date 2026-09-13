package com.mysteriacraft.core.reward;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Distribue une Reward a un joueur (credit d'economie ou objet dans l'inventaire, avec depot
 * au sol si plein) et joue le message "inventaire plein" si necessaire. Partage entre les
 * modules Crates, BattlePass et Quetes pour eviter de dupliquer cette logique.
 */
public final class RewardGiver {

    private RewardGiver() {
    }

    public static void give(Player player, Reward reward, EconomyManager economyManager, MessageManager messages) {
        if (reward.type() == RewardType.ECONOMIE) {
            economyManager.deposit(player.getUniqueId(), reward.economyAmount());
            return;
        }
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
