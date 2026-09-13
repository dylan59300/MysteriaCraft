package com.mysteriacraft.luckyblock.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Marque un bloc pose comme Lucky Block s'il provient d'un item marque (craft/achat/recompense),
 * et delegue sa casse au LuckyBlockService s'il s'agit bien d'un Lucky Block.
 * Gere aussi le bonus de minerais : poser un minerai a cote d'un Lucky Block (ou l'inverse)
 * augmente sa chance d'effet BON, selon le minerai (voir bonus-minerais dans luckyblocks.yml).
 */
public class LuckyBlockListener implements Listener {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final LuckyBlockManager manager;
    private final LuckyBlockService service;
    private final MessageManager messages;

    public LuckyBlockListener(LuckyBlockManager manager, LuckyBlockService service, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        String familyId = manager.getFamilyIdFromItem(event.getItemInHand());

        if (familyId != null) {
            // Un Lucky Block est pose : on marque le bloc et on recupere le bonus des minerais deja voisins.
            manager.tagBlock(placed, familyId);
            double surroundingBonus = manager.computeSurroundingOreBonus(placed);
            if (surroundingBonus > 0) {
                manager.addBonus(placed, surroundingBonus);
            }
            return;
        }

        if (!manager.isBonusOre(placed.getType())) {
            return;
        }

        // Un minerai bonus est pose : on cherche un Lucky Block voisin pour lui donner le bonus.
        double oreBonus = manager.getOreBonus(placed.getType());
        for (BlockFace face : FACES) {
            Block neighbor = placed.getRelative(face);
            LuckyBlockFamily family = manager.getFamilyOfBlock(neighbor);
            if (family == null) {
                continue;
            }
            double newTotal = manager.addBonus(neighbor, oreBonus);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("bonus", String.valueOf((int) oreBonus));
            placeholders.put("total", String.valueOf((int) Math.min(newTotal, manager.getBonusMax())));
            placeholders.put("max", String.valueOf((int) manager.getBonusMax()));
            messages.send(event.getPlayer(), "luckyblock.bonus-augmente", placeholders);
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
