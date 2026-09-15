package com.mysteriacraft.marchand.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.marchand.MarchandManager;
import com.mysteriacraft.marchand.MarchandService;
import com.mysteriacraft.marchand.gui.MarchandGui;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Invoque le PNJ Marchand (clic-droit sur un bloc avec "oeuf_pnj_marchand" en main), ouvre son
 * menu d'echange (clic sur lui), et le protege de tout dommage (en plus de setInvulnerable).
 */
public class MarchandListener implements Listener {

    private final MarchandManager manager;
    private final MarchandService service;
    private final CustomItemManager customItemManager;
    private final MessageManager messages;

    public MarchandListener(MarchandManager manager, MarchandService service,
                             CustomItemManager customItemManager, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.customItemManager = customItemManager;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!"oeuf_pnj_marchand".equalsIgnoreCase(customItemManager.getCustomItemId(item))) {
            return;
        }
        event.setCancelled(true);

        Location spawnLocation = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        service.spawnNpc(spawnLocation);

        if (player.getGameMode() != GameMode.CREATIVE) {
            int remaining = item.getAmount() - 1;
            if (remaining > 0) {
                item.setAmount(remaining);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }
        messages.send(player, "marchand.invoque");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !service.isMarchand(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        new MarchandGui(event.getPlayer(), manager, service, messages).open();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (service.isMarchand(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
