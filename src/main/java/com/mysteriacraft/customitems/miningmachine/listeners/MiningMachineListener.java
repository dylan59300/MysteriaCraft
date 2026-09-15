package com.mysteriacraft.customitems.miningmachine.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.miningmachine.MiningMachineManager;
import com.mysteriacraft.customitems.miningmachine.MiningMachineService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

/**
 * Marque la Machine a Miner a sa pose (en respectant la limite par joueur), empeche de la casser
 * pendant qu'elle mine (sauf en sneak ou avec la permission admin) et delegue le clic-droit dessus
 * au MiningMachineService.
 */
public class MiningMachineListener implements Listener {

    private final MiningMachineManager manager;
    private final MiningMachineService service;
    private final MessageManager messages;

    public MiningMachineListener(MiningMachineManager manager, MiningMachineService service, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!manager.isMachineItem(event.getItemInHand())) {
            return;
        }

        int max = manager.getMaxPerPlayer();
        if (max > 0 && manager.countOwnedMachines(event.getPlayer().getUniqueId()) >= max) {
            event.setCancelled(true);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("max", String.valueOf(max));
            messages.send(event.getPlayer(), "machine-miniere.limite-atteinte", placeholders);
            return;
        }

        manager.tagBlock(event.getBlockPlaced(), event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!manager.isMachineBlock(block)) {
            return;
        }

        if (manager.isActive(block) && !event.getPlayer().isSneaking()
                && !event.getPlayer().hasPermission("mysteriacraft.machineminiere.admin")) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "machine-miniere.protection-active");
            return;
        }

        // Ordre important : removeHologram() lit l'etat de la machine (encore present dans le
        // cache) pour retrouver son hologramme ; forgetMachine() supprime cet etat juste apres.
        manager.removeHologram(block);
        manager.forgetMachine(block.getLocation());
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation(), manager.createMachineItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!manager.isMachineBlock(block)) {
            return;
        }
        event.setCancelled(true);
        service.handleInteract(event.getPlayer(), block);
    }
}
