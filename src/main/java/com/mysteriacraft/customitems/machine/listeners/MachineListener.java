package com.mysteriacraft.customitems.machine.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.customitems.machine.MachineManager;
import com.mysteriacraft.customitems.machine.MachineService;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Marque la Machine a Transformation a sa pose, la fait dropper elle-meme a la casse (au lieu
 * du bloc brut) avec un effet d'explosion visuelle si elle etait encore chargee en carburant,
 * empeche de la casser pendant qu'elle recharge (sauf en sneak ou avec la permission admin),
 * delegue le clic-droit dessus au MachineService, et active le boost "carburant illimite"
 * (item consommable standalone, pas besoin de viser une machine) en clic-droit.
 */
public class MachineListener implements Listener {

    private final MachineManager manager;
    private final MachineService service;
    private final CustomItemManager customItemManager;
    private final MessageManager messages;

    public MachineListener(MachineManager manager, MachineService service,
                            CustomItemManager customItemManager, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.customItemManager = customItemManager;
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String customItemId = customItemManager.getCustomItemId(inHand);
        // Boost "carburant illimite" : consommable STANDALONE, pas besoin de viser une machine.
        if (customItemId != null && customItemId.equalsIgnoreCase(manager.getFuelBoostItemId())) {
            event.setCancelled(true);
            activateFuelBoost(player, inHand);
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
        service.handleInteract(player, block);
    }

    private void activateFuelBoost(Player player, ItemStack item) {
        int remaining = item.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(item, remaining) : null);

        long durationSeconds = manager.getFuelBoostDurationSeconds();
        manager.activateFuelBoost(player.getUniqueId(), durationSeconds);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("duree", formatDuration(durationSeconds * 1000L));
        messages.send(player, "machine.boost-carburant-active", placeholders);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    /** Formate une duree en millisecondes en "XhYmZs" (n'affiche que les unites non nulles). */
    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours).append("h");
        }
        if (minutes > 0) {
            builder.append(minutes).append("m");
        }
        if (hours == 0 && (seconds > 0 || builder.isEmpty())) {
            builder.append(seconds).append("s");
        }
        return builder.toString();
    }
}
