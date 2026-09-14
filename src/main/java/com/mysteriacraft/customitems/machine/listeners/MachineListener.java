package com.mysteriacraft.customitems.machine.listeners;

import com.mysteriacraft.customitems.machine.MachineManager;
import com.mysteriacraft.customitems.machine.MachineService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Marque la Machine a Transformation a sa pose, la fait dropper elle-meme a la casse (au lieu
 * du bloc brut), et delegue le clic-droit dessus au MachineService.
 */
public class MachineListener implements Listener {

    private final MachineManager manager;
    private final MachineService service;

    public MachineListener(MachineManager manager, MachineService service) {
        this.manager = manager;
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (manager.isMachineItem(event.getItemInHand())) {
            manager.tagBlock(event.getBlockPlaced());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!manager.isMachineBlock(block)) {
            return;
        }
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation(), manager.createMachineItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // Ignore l'evenement duplique de la main secondaire pour ne traiter le clic qu'une fois.
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
        service.attemptTransformation(event.getPlayer(), block);
    }
}
