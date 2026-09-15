package com.mysteriacraft.island.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.island.IslandManager;
import com.mysteriacraft.island.IslandService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Protege les Iles : seuls le proprietaire et ses membres de confiance peuvent casser/poser des
 * blocs ou ouvrir un conteneur sur une ile (permission mysteriacraft.island.admin en bypass).
 * Bloque aussi tout build/casse dans le "vide" entre 2 iles (aucune ile ne le revendique). PVP et
 * explosions desactivables globalement (pvp-autorise / explosions-autorisees dans islands.yml).
 * Met a jour la valeur d'ile (et distribue les paliers) a chaque pose/casse reussie.
 */
public class IslandProtectionListener implements Listener {

    private final IslandManager manager;
    private final IslandService service;
    private final MessageManager messages;

    public IslandProtectionListener(IslandManager manager, IslandService service, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    private boolean isIslandWorld(Location location) {
        return location.getWorld() != null && location.getWorld().equals(manager.getWorld());
    }

    /** True si ce joueur peut construire/casser/interagir a cette position (bypass admin inclus). */
    private boolean canBuild(Player player, Location location, IslandManager.Island[] islandOut) {
        if (!isIslandWorld(location)) {
            return true;
        }
        if (player.hasPermission("mysteriacraft.island.admin")) {
            return true;
        }
        IslandManager.Island island = manager.getIslandAt(location.getBlockX(), location.getBlockZ());
        islandOut[0] = island;
        return island != null && island.isTrusted(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        IslandManager.Island[] islandOut = new IslandManager.Island[1];
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation(), islandOut)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "ile.protection-refusee");
            return;
        }
        if (islandOut[0] != null) {
            service.onBlockBroken(islandOut[0], event.getBlock().getType());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        IslandManager.Island[] islandOut = new IslandManager.Island[1];
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation(), islandOut)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "ile.protection-refusee");
            return;
        }
        if (islandOut[0] != null) {
            service.onBlockPlaced(islandOut[0], event.getBlock().getType());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Container)) {
            return;
        }
        IslandManager.Island[] islandOut = new IslandManager.Island[1];
        if (!canBuild(event.getPlayer(), block.getLocation(), islandOut)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "ile.protection-refusee");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (manager.isPvpAllowed()) {
            return;
        }
        if (!isIslandWorld(event.getEntity().getLocation())) {
            return;
        }
        if (!(event.getEntity() instanceof Player) || !(resolveAttacker(event.getDamager()) instanceof Player)) {
            return;
        }
        event.setCancelled(true);
    }

    private Entity resolveAttacker(Entity damager) {
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (manager.areExplosionsAllowed()) {
            return;
        }
        if (!isIslandWorld(event.getLocation())) {
            return;
        }
        event.blockList().clear();
    }
}
