package com.mysteriacraft.hybride.listeners;

import com.mysteriacraft.hybride.HybridGeneratorManager;
import com.mysteriacraft.hybride.HybridGeneratorService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Pose, casse et clic-droit sur un Generateur Hybride. */
public class HybridGeneratorListener implements Listener {

    private final HybridGeneratorManager manager;
    private final HybridGeneratorService service;

    public HybridGeneratorListener(HybridGeneratorManager manager, HybridGeneratorService service) {
        this.manager = manager;
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        HybridGeneratorManager.GeneratorType type = manager.getItemGeneratorType(item);
        if (type == null) {
            return;
        }
        manager.tagBlock(event.getBlockPlaced(), type, event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!manager.isGeneratorBlock(block)) {
            return;
        }
        HybridGeneratorManager.GeneratorType type = manager.getBlockType(block);
        manager.forgetGenerator(block.getLocation());
        event.setDropItems(false);
        if (type != null) {
            block.getWorld().dropItemNaturally(block.getLocation(), manager.createGeneratorItem(type, 1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !manager.isGeneratorBlock(block)) {
            return;
        }
        event.setCancelled(true);
        service.handleInteract(event.getPlayer(), block);
    }
}
