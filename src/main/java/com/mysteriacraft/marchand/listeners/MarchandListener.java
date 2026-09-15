package com.mysteriacraft.marchand.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.marchand.MarchandDefinition;
import com.mysteriacraft.marchand.MarchandManager;
import com.mysteriacraft.marchand.MarchandService;
import com.mysteriacraft.marchand.gui.MarchandGui;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Invoque le PNJ Marchand correspondant a l'item custom tenu en main (clic-droit sur un bloc),
 * ouvre son menu d'echange (clic sur lui), le protege de tout dommage (en plus de setInvulnerable),
 * et fait tomber occasionnellement des pieces d'echange en minant/tuant un mob (voir "drop-passif").
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
        String customId = customItemManager.getCustomItemId(item);
        MarchandDefinition definition = manager.getMarchandByOeufId(customId);
        if (definition == null) {
            return;
        }
        event.setCancelled(true);

        Location spawnLocation = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        service.spawnNpc(spawnLocation, definition);

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
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String marchandId = service.getMarchandId(event.getRightClicked());
        MarchandDefinition definition = manager.getMarchand(marchandId);
        if (definition == null) {
            return;
        }
        event.setCancelled(true);
        new MarchandGui(event.getPlayer(), definition, service, messages).open();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (service.getMarchandId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    // ---- Drop passif de pieces d'echange (voir "drop-passif" dans marchand.yml) ----

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        double chance = manager.getDropPassifChanceMinage();
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble(100) >= chance) {
            return;
        }
        dropPassifPiece(event.getBlock().getWorld(), event.getBlock().getLocation().add(0.5, 0.5, 0.5));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getKiller() == null || service.getMarchandId(entity) != null) {
            return;
        }
        double chance = manager.getDropPassifChanceMob();
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble(100) >= chance) {
            return;
        }
        dropPassifPiece(entity.getWorld(), entity.getLocation());
    }

    private void dropPassifPiece(World world, Location location) {
        var definition = customItemManager.getItem(manager.getDropPassifItemId());
        if (definition == null) {
            return;
        }
        ItemStack drop = customItemManager.createItem(definition, manager.getDropPassifQuantite());
        world.dropItemNaturally(location, drop);
    }
}
