package com.mysteriacraft.luckyblock.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Clic-droit avec un Lucky Block EN MAIN (dans l'air ou sur un bloc) : declenche immediatement
 * un effet aleatoire et consomme UN exemplaire. Le bloc ne se pose jamais dans le monde.
 */
public class LuckyBlockListener implements Listener {

    private final LuckyBlockManager manager;
    private final LuckyBlockService service;
    private final MessageManager messages;

    public LuckyBlockListener(LuckyBlockManager manager, LuckyBlockService service, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        String familyId = manager.getFamilyIdFromItem(item);
        if (familyId == null) {
            return;
        }
        LuckyBlockFamily family = manager.getFamily(familyId);
        if (family == null) {
            return;
        }

        event.setCancelled(true);

        // Consomme un exemplaire.
        int remaining = item.getAmount() - 1;
        Player player = event.getPlayer();
        if (remaining > 0) {
            item.setAmount(remaining);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        service.handleRightClick(player, family);
    }

    /** Kit de connexion (voir luckyblocks.yml: kit-connexion) : donne "quantite" exemplaires de
     * CHAQUE famille de Lucky Block actuellement active, au maximum UNE FOIS PAR JOUR par joueur
     * (voir LuckyBlockManager#hasReceivedJoinKitToday). Une reconnexion le meme jour ne redonne rien. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!manager.isJoinKitEnabled() || manager.getJoinKitQuantity() <= 0) {
            return;
        }
        Player player = event.getPlayer();
        if (manager.hasReceivedJoinKitToday(player.getUniqueId())) {
            return;
        }
        int quantity = manager.getJoinKitQuantity();
        for (LuckyBlockFamily family : manager.getFamiliesSorted()) {
            ItemStack lbItem = manager.createItem(family, quantity);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(lbItem);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }
        manager.markJoinKitReceivedToday(player.getUniqueId());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(quantity));
        messages.send(player, "luckyblock.kit-connexion", placeholders);
    }
}
