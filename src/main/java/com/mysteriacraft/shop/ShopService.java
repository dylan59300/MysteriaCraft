package com.mysteriacraft.shop;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.core.reward.RewardType;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.rank.RankManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Traite les achats/reventes de la Boutique : l'achat delegue simplement au RewardGiver partage
 * (n'importe quel type de recompense fonctionne), la revente ne s'applique qu'aux articles
 * ITEM/OBJET_CUSTOM et se base sur l'item REELLEMENT tenu en main par le joueur.
 */
public class ShopService {

    private final EconomyManager economyManager;
    private final RewardGiver rewardGiver;
    private final CustomItemManager customItemManager;
    private final RankManager rankManager;
    private final MessageManager messages;

    public ShopService(EconomyManager economyManager, RewardGiver rewardGiver, CustomItemManager customItemManager,
                        RankManager rankManager, MessageManager messages) {
        this.economyManager = economyManager;
        this.rewardGiver = rewardGiver;
        this.customItemManager = customItemManager;
        this.rankManager = rankManager;
        this.messages = messages;
    }

    public void buy(Player player, ShopManager.ShopItem item) {
        if (!item.isPurchasable()) {
            messages.send(player, "boutique.non-achetable");
            return;
        }
        if (!economyManager.withdraw(player.getUniqueId(), item.buyPrice())) {
            messages.send(player, "boutique.fonds-insuffisants");
            return;
        }
        rewardGiver.give(player, item.reward());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", item.displayName());
        placeholders.put("prix", economyManager.format(item.buyPrice()));
        messages.send(player, "boutique.achat-reussi", placeholders);
    }

    /** Revend TOUT le stack en main du joueur, si celui-ci correspond a l'article (meme materiau
     * vanilla, ou meme id d'item custom). Rien n'est retire si aucune correspondance. */
    public void sell(Player player, ShopManager.ShopItem item) {
        if (!item.isSellable()) {
            messages.send(player, "boutique.non-vendable");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        int amount = matchingAmount(hand, item.reward());
        if (amount <= 0) {
            messages.send(player, "boutique.rien-a-vendre");
            return;
        }

        double bonusPercent = rankManager.getBonusVentePourcent(player.getUniqueId());
        double total = amount * item.sellPrice() * (1.0 + bonusPercent / 100.0);
        player.getInventory().setItemInMainHand(null);
        economyManager.deposit(player.getUniqueId(), total);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(amount));
        placeholders.put("item", item.displayName());
        placeholders.put("montant", economyManager.format(total));
        messages.send(player, "boutique.vente-reussie", placeholders);
    }

    private int matchingAmount(ItemStack hand, Reward reward) {
        if (hand == null || hand.getType() == Material.AIR) {
            return 0;
        }
        if (reward.type() == RewardType.ITEM) {
            return reward.item() != null && hand.getType() == reward.item().getType() ? hand.getAmount() : 0;
        }
        if (reward.type() == RewardType.OBJET_CUSTOM) {
            String heldId = customItemManager.getCustomItemId(hand);
            return reward.customItemId() != null && reward.customItemId().equalsIgnoreCase(heldId) ? hand.getAmount() : 0;
        }
        return 0;
    }
}
