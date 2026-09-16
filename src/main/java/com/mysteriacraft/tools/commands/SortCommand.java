package com.mysteriacraft.tools.commands;

import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /trier : range et fusionne automatiquement l'inventaire principal du joueur (36 cases, hors
 * armure/offhand), en regroupant les stacks similaires (voir ItemStack#isSimilar, qui distingue
 * bien les items custom entre eux) et en triant par materiau/nom.
 */
public class SortCommand implements CommandExecutor {

    private final MessageManager messages;

    public SortCommand(MessageManager messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        ItemStack[] storage = player.getInventory().getStorageContents();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : storage) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                items.add(item);
            }
        }

        List<ItemStack> merged = mergeStacks(items);
        merged.sort(Comparator.comparing((ItemStack item) -> item.getType().name())
                .thenComparing(item -> item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? item.getItemMeta().getDisplayName() : ""));

        ItemStack[] newStorage = new ItemStack[storage.length];
        for (int i = 0; i < merged.size() && i < newStorage.length; i++) {
            newStorage[i] = merged.get(i);
        }
        player.getInventory().setStorageContents(newStorage);

        messages.send(player, "trier.effectue");
        return true;
    }

    private List<ItemStack> mergeStacks(List<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : items) {
            ItemStack remaining = item.clone();
            for (ItemStack existing : result) {
                if (remaining.getAmount() <= 0) {
                    break;
                }
                if (!existing.isSimilar(remaining)) {
                    continue;
                }
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space <= 0) {
                    continue;
                }
                int transfer = Math.min(space, remaining.getAmount());
                existing.setAmount(existing.getAmount() + transfer);
                remaining.setAmount(remaining.getAmount() - transfer);
            }
            if (remaining.getAmount() > 0) {
                result.add(remaining);
            }
        }
        return result;
    }
}
