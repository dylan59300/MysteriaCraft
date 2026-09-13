package com.mysteriacraft.luckyblock.listeners;

import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Marque un bloc pose comme Lucky Block s'il provient d'un item marque (craft/achat/recompense),
 * et delegue sa casse au LuckyBlockService s'il s'agit bien d'un Lucky Block.
 */
public class LuckyBlockListener implements Listener {

    private final LuckyBlockManager manager;
    private final LuckyBlockService service;

    public LuckyBlockListener(LuckyBlockManager manager, LuckyBlockService service) {
        this.manager = manager;
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        String familyId = manager.getFamilyIdFromItem(event.getItemInHand());
        if (familyId != null) {
            manager.tagBlock(event.getBlockPlaced(), familyId);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        LuckyBlockFamily family = manager.getFamilyOfBlock(block);
        if (family != null) {
            service.handleBreak(event, family);
        }
    }
}
