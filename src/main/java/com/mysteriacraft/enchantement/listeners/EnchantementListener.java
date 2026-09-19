package com.mysteriacraft.enchantement.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.enchantement.EnchantementManager;
import com.mysteriacraft.enchantement.EnchantementService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Table d'Enchantement Custom : clic-droit sur le bloc configure (voir "materiau-table") avec le
 * catalyseur en main secondaire et l'item a enchanter en main principale. Consomme le catalyseur
 * et des niveaux d'XP, puis applique un enchantement aleatoire du pool (voir EnchantementService).
 */
public class EnchantementListener implements Listener {

    private final EnchantementManager manager;
    private final EnchantementService service;
    private final CustomItemManager customItemManager;
    private final MessageManager messages;

    public EnchantementListener(EnchantementManager manager, EnchantementService service,
                                 CustomItemManager customItemManager, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.customItemManager = customItemManager;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock().getType() != manager.getMateriauTable()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!manager.getCatalyseurId().equalsIgnoreCase(customItemManager.getCustomItemId(offHand))) {
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType().isAir() || !mainHand.hasItemMeta()) {
            messages.send(player, "enchantement.aucun-item");
            return;
        }

        int cout = manager.getCoutXpNiveaux();
        if (player.getLevel() < cout) {
            messages.send(player, "enchantement.xp-insuffisant");
            return;
        }

        event.setCancelled(true);

        if (service.enchant(player, mainHand) == null) {
            return;
        }

        player.setLevel(player.getLevel() - cout);
        int remaining = offHand.getAmount() - 1;
        if (remaining > 0) {
            offHand.setAmount(remaining);
        } else {
            player.getInventory().setItemInOffHand(null);
        }
    }
}
