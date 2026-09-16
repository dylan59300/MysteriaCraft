package com.mysteriacraft.luckyblock.generator.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.generator.LuckyBlockGeneratorManager;
import com.mysteriacraft.luckyblock.generator.LuckyBlockGeneratorService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Marque un bloc pose comme Generateur de Lucky Block s'il provient d'un item marque (craft/give/
 * recompense), delegue son alimentation/consultation d'etat au LuckyBlockGeneratorService, et
 * rend son item (avec la MEME famille) a la casse plutot que de le faire disparaitre.
 */
public class LuckyBlockGeneratorListener implements Listener {

    private final LuckyBlockGeneratorManager manager;
    private final LuckyBlockGeneratorService service;
    private final MessageManager messages;

    public LuckyBlockGeneratorListener(LuckyBlockGeneratorManager manager, LuckyBlockGeneratorService service,
                                        MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        String familyId = manager.getFamilyIdFromItem(item);
        if (familyId == null) {
            return;
        }
        Player player = event.getPlayer();
        if (manager.getMaxPerPlayer() > 0 && manager.countOwnedGenerators(player.getUniqueId()) >= manager.getMaxPerPlayer()) {
            event.setCancelled(true);
            messages.send(player, "generateurlb.limite-atteinte");
            return;
        }
        manager.tagBlock(event.getBlockPlaced(), player.getUniqueId(), familyId);
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
        service.handleInteract(event.getPlayer(), block, event.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!manager.isGeneratorBlock(block)) {
            return;
        }
        LuckyBlockFamily family = manager.getFamily(block);
        event.setDropItems(false);
        if (family != null) {
            block.getWorld().dropItemNaturally(block.getLocation(), manager.createItem(family, 1));
        }
        manager.removeHologram(block);
        manager.forgetGenerator(new Location(block.getWorld(), block.getX(), block.getY(), block.getZ()));
    }
}
