package com.mysteriacraft.customitems.listeners;

import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.customitems.CustomItemService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Drops de minerais custom, durabilite custom (outils/armes) et enchantement custom "vol de vie"
 * (voir CustomItemService), plus la reparation via "kit_reparation" (clic droit, kit en main
 * principale + item abime en main secondaire, en etant accroupi).
 */
public class CustomItemListener implements Listener {

    private final CustomItemService service;
    private final CustomItemManager manager;

    public CustomItemListener(CustomItemService service, CustomItemManager manager) {
        this.service = service;
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        service.handleOreBreak(event);
        service.handleToolDurability(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        service.handleMeleeHit(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!"kit_reparation".equalsIgnoreCase(manager.getCustomItemId(mainHand))) {
            return;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (manager.getCustomItemId(offHand) == null) {
            return;
        }
        event.setCancelled(true);
        service.repairItem(player, offHand, mainHand);
    }
}
