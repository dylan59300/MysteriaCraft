package com.mysteriacraft.economy.generator.listeners;

import com.mysteriacraft.economy.generator.GeneratorManager;
import com.mysteriacraft.economy.generator.GeneratorService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Marque un Generateur d'Argent a sa pose, recupere automatiquement son stock pour le joueur
 * qui le casse avant de le faire retomber en item, et delegue le clic-droit dessus au GeneratorService.
 */
public class GeneratorListener implements Listener {

    private final GeneratorManager manager;
    private final GeneratorService service;

    public GeneratorListener(GeneratorManager manager, GeneratorService service) {
        this.manager = manager;
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!manager.isGeneratorItem(item)) {
            return;
        }
        GeneratorManager.GeneratorType type = manager.getItemGeneratorType(item);
        if (type == null) {
            return;
        }
        manager.tagBlock(event.getBlockPlaced(), type);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!manager.isGeneratorBlock(block)) {
            return;
        }
        GeneratorManager.GeneratorType type = manager.getBlockType(block);

        // Recupere automatiquement l'argent accumule pour le joueur avant que le bloc ne disparaisse.
        service.collectOnBreak(event.getPlayer(), block);

        manager.removeHologram(block);
        manager.forgetGenerator(block.getLocation());
        event.setDropItems(false);
        if (type != null) {
            block.getWorld().dropItemNaturally(block.getLocation(), manager.createGeneratorItem(type));
        }
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
        if (!manager.isGeneratorBlock(block)) {
            return;
        }
        event.setCancelled(true);
        service.handleInteract(event.getPlayer(), block);
    }
}
