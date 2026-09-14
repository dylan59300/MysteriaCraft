package com.mysteriacraft.customitems.machine.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.machine.MachineManager;
import com.mysteriacraft.customitems.machine.MachineService;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
 * du bloc brut) avec un effet d'explosion visuelle si elle etait encore chargee en carburant,
 * empeche de la casser pendant qu'elle recharge (sauf en sneak ou avec la permission admin),
 * et delegue le clic-droit dessus au MachineService.
 */
public class MachineListener implements Listener {

    private final MachineManager manager;
    private final MachineService service;
    private final MessageManager messages;

    public MachineListener(MachineManager manager, MachineService service, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.messages = messages;
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

        // Protection : impossible de casser une machine en pleine recharge, sauf en sneak
        // (bris volontaire assume) ou avec la permission admin.
        if (manager.getRemainingCooldownMillis(block) > 0
                && !event.getPlayer().isSneaking()
                && !event.getPlayer().hasPermission("mysteriacraft.machine.admin")) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "machine.protection-cooldown");
            return;
        }

        // Casser une machine encore chargee en carburant est risque : simple effet visuel/sonore,
        // aucun degat reel n'est inflige au monde (pas de bloc detruit ni de joueur blesse).
        if (manager.getFuel(block) > 0) {
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            center.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, center, 3, 0.2, 0.2, 0.2);
            center.getWorld().spawnParticle(Particle.SMOKE_LARGE, center, 25, 0.4, 0.4, 0.4);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);
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
        service.handleInteract(event.getPlayer(), block);
    }
}
