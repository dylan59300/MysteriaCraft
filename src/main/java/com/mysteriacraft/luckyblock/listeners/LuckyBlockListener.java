package com.mysteriacraft.luckyblock.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import com.mysteriacraft.luckyblock.gui.LuckyBlockOddsGui;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Marque un bloc pose comme Lucky Block s'il provient d'un item marque (craft/achat/recompense),
 * et delegue sa casse au LuckyBlockService s'il s'agit bien d'un Lucky Block.
 * Gere aussi le bonus de minerais : poser un minerai a cote d'un Lucky Block (ou l'inverse)
 * augmente sa chance d'effet BON, selon le minerai (voir bonus-minerais dans luckyblocks.yml).
 * Un clic droit avec un Lucky Block en main (sans etre accroupi sur un bloc, pour ne pas gener
 * la pose normale) ouvre le GUI des loot disponibles pour cette famille.
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
    public void onInteractWithItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        String familyId = manager.getFamilyIdFromItem(event.getItem());
        if (familyId == null) {
            return;
        }
        // Accroupi + clic sur un bloc : on laisse la pose normale se derouler.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getPlayer().isSneaking()) {
            return;
        }
        LuckyBlockFamily family = manager.getFamily(familyId);
        if (family == null) {
            return;
        }
        event.setCancelled(true);
        new LuckyBlockOddsGui(event.getPlayer(), family, manager, null, messages).open();
    }

    /** Kit de connexion (voir luckyblocks.yml: kit-connexion) : donne "quantite" exemplaires de
     * CHAQUE famille de Lucky Block actuellement active a chaque connexion du joueur. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!manager.isJoinKitEnabled() || manager.getJoinKitQuantity() <= 0) {
            return;
        }
        Player player = event.getPlayer();
        int quantity = manager.getJoinKitQuantity();
        for (LuckyBlockFamily family : manager.getFamiliesSorted()) {
            ItemStack item = manager.createItem(family, quantity);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(quantity));
        messages.send(player, "luckyblock.kit-connexion", placeholders);
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
