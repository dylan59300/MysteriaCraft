package com.mysteriacraft.raffinerie.listeners;

import com.mysteriacraft.raffinerie.RaffinerieManager;
import com.mysteriacraft.raffinerie.RaffinerieService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Intercepte le clic-droit sur le bloc de Raffinerie AVANT que Bukkit n'ouvre son interface
 * vanilla (le bloc reutilise un materiau existant, voir RaffinerieManager). */
public class RaffinerieListener implements Listener {

    private final RaffinerieManager manager;
    private final RaffinerieService service;

    public RaffinerieListener(RaffinerieManager manager, RaffinerieService service) {
        this.manager = manager;
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !manager.isRaffinerieBlock(block.getType())) {
            return;
        }
        event.setCancelled(true);
        service.handleInteract(event.getPlayer(), block);
    }
}
