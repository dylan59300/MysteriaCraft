package com.mysteriacraft.recyclage;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Traite /recycler : consomme l'item custom use tenu en main et rend ses materiaux de base. */
public class RecyclageService {

    private final RecyclageManager manager;
    private final CustomItemManager customItemManager;
    private final MessageManager messages;

    public RecyclageService(RecyclageManager manager, CustomItemManager customItemManager, MessageManager messages) {
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.messages = messages;
    }

    public void recycler(Player player) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String customItemId = customItemManager.getCustomItemId(inHand);
        if (customItemId == null || !manager.isRecyclable(customItemId)) {
            messages.send(player, "recyclage.non-recyclable");
            return;
        }

        int remaining = inHand.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);

        for (RecyclageManager.MaterialReward reward : manager.getRewards(customItemId)) {
            ItemStack drop = new ItemStack(reward.material(), reward.amount());
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(drop);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("materiaux", manager.getRewards(customItemId).stream()
                .map(r -> r.amount() + "x " + r.material().name().replace('_', ' '))
                .collect(Collectors.joining(", ")));
        messages.send(player, "recyclage.reussi", placeholders);
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
