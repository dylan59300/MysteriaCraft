package com.mysteriacraft.customitems;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gere le tirage des drops de minerais custom a la casse d'un bloc source, ainsi que la
 * revente d'objets custom contre la monnaie interne (/customitem sell).
 */
public class CustomItemService implements RewardGiver.CustomItemGiveHandler {

    private final CustomItemManager manager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public CustomItemService(CustomItemManager manager, EconomyManager economyManager, MessageManager messages) {
        this.manager = manager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    /** Appele par le listener a la casse d'un bloc : tire chaque item custom eligible independamment. */
    public void handleOreBreak(BlockBreakEvent event) {
        List<CustomItemDefinition> candidates = manager.getItemsForOre(event.getBlock().getType());
        if (candidates.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();

        for (CustomItemDefinition definition : candidates) {
            if (ThreadLocalRandom.current().nextDouble(100.0) >= definition.dropChance()) {
                continue;
            }
            ItemStack item = manager.createItem(definition);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("item", definition.displayName());
            messages.send(player, "customitem.trouve", placeholders);
        }
    }

    /** Donne un item custom gratuitement (recompense de crate/battlepass/quete/luckyblock). */
    @Override
    public void giveCustomItem(Player player, String customItemId, int amount) {
        CustomItemDefinition definition = manager.getItem(customItemId);
        if (definition == null) {
            return;
        }
        ItemStack item = manager.createItem(definition, amount);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    /** Vend tous les exemplaires d'un item custom presents dans l'inventaire du joueur. */
    public void sellAll(Player player, String itemId) {
        CustomItemDefinition definition = manager.getItem(itemId);
        if (definition == null) {
            messages.send(player, "customitem.introuvable");
            return;
        }
        if (!definition.isSellable()) {
            messages.send(player, "customitem.non-vendable");
            return;
        }

        ItemStack[] contents = player.getInventory().getContents();
        int totalFound = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && definition.id().equals(manager.getCustomItemId(stack))) {
                totalFound += stack.getAmount();
                player.getInventory().setItem(i, null);
            }
        }

        if (totalFound == 0) {
            messages.send(player, "customitem.aucun-en-inventaire");
            return;
        }

        double total = definition.sellPrice() * totalFound;
        economyManager.deposit(player.getUniqueId(), total);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(totalFound));
        placeholders.put("item", definition.displayName());
        placeholders.put("total", economyManager.format(total));
        messages.send(player, "customitem.vente-reussie", placeholders);
    }
}
