package com.mysteriacraft.customitems.miningmachine;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traite l'utilisation de la Machine a Miner :
 * - clic-droit avec un carburant en main -> ajoute des blocs minables et (re)demarre une passe
 *   sur le chunk actuel si elle etait a l'arret ;
 * - clic-droit a vide -> affiche la progression (carburant restant, % du chunk mine) ;
 * - sneak + clic-droit a vide -> arrete la passe en cours (le carburant restant est conserve).
 * Les blocs mines sont deposes dans un conteneur colle a la machine, ou au sol a defaut.
 */
public class MiningMachineService {

    private final MiningMachineManager manager;
    private final CustomItemManager customItemManager;
    private final MessageManager messages;

    public MiningMachineService(MiningMachineManager manager, CustomItemManager customItemManager, MessageManager messages) {
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.messages = messages;
    }

    public void handleInteract(Player player, Block machineBlock) {
        ItemStack inHand = player.getInventory().getItemInMainHand();

        if (inHand.getType() == Material.AIR) {
            if (player.isSneaking() && manager.isActive(machineBlock)) {
                manager.stop(machineBlock);
                messages.send(player, "machine-miniere.arretee");
                return;
            }
            showStatus(player, machineBlock);
            return;
        }

        String customItemId = customItemManager.getCustomItemId(inHand);
        MiningMachineManager.FuelType fuelType = manager.getFuelType(customItemId);
        if (fuelType == null) {
            messages.send(player, "machine-miniere.carburant-non-accepte");
            return;
        }

        boolean wasActive = manager.isActive(machineBlock);
        int remaining = inHand.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);
        manager.refuel(machineBlock, fuelType.blocs());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("blocs", String.valueOf(fuelType.blocs()));
        placeholders.put("total", String.valueOf(manager.getFuel(machineBlock)));
        messages.send(player, wasActive ? "machine-miniere.ravitaillee" : "machine-miniere.demarree", placeholders);

        Location loc = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 10, 0.4, 0.4, 0.4);
        player.playSound(loc, Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
        updateHologram(machineBlock);
    }

    private void showStatus(Player player, Block machineBlock) {
        int progress = progressPercent(machineBlock);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("carburant", String.valueOf(manager.getFuel(machineBlock)));
        placeholders.put("progres", String.valueOf(progress));

        if (manager.isActive(machineBlock)) {
            messages.send(player, "machine-miniere.statut-active", placeholders);
        } else {
            messages.send(player, "machine-miniere.statut-arretee", placeholders);
        }
        updateHologram(machineBlock);
    }

    private int progressPercent(Block machineBlock) {
        long total = manager.getTotalBlocks(machineBlock);
        long mined = manager.getMinedBlocks(machineBlock);
        return total > 0 ? (int) (100.0 * mined / total) : 0;
    }

    /** Met a jour le texte de l'hologramme : carburant restant et % du chunk actuel mine, ou "a
     * l'arret" si aucune passe n'est en cours. */
    private void updateHologram(Block machineBlock) {
        ArmorStand stand = manager.getHologram(machineBlock);
        if (stand == null) {
            return;
        }
        String name;
        if (manager.isActive(machineBlock)) {
            name = "&b&lMachine a Miner &7- &e" + manager.getFuel(machineBlock) + " &7bloc(s) &7- &a"
                    + progressPercent(machineBlock) + "%";
        } else {
            name = "&b&lMachine a Miner &7- &e" + manager.getFuel(machineBlock) + " &7bloc(s) &7- &7A l'arret";
        }
        stand.setCustomName(MessageManager.color(name));
    }

    /**
     * Appele periodiquement (voir MysteriaCraft) pour chaque machine posee : fait avancer sa
     * progression de minage et depose les blocs mines dans son conteneur de sortie (ou au sol).
     */
    public void tickAll() {
        List<Location> stale = new ArrayList<>();
        for (Location location : manager.getActiveMachineLocations()) {
            org.bukkit.World world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            Block block = location.getBlock();
            if (!manager.isMachineBlock(block)) {
                stale.add(location);
                continue;
            }
            if (!manager.isActive(block)) {
                continue;
            }

            Block outputBlock = manager.getOutputContainer(block);
            Inventory output = (outputBlock != null && outputBlock.getState() instanceof Container container)
                    ? container.getInventory() : null;

            manager.tick(block, (minedBlock, material) -> {
                ItemStack drop = new ItemStack(material);
                if (output != null) {
                    Map<Integer, ItemStack> leftovers = output.addItem(drop);
                    if (!leftovers.isEmpty()) {
                        leftovers.values().forEach(leftover ->
                                block.getWorld().dropItemNaturally(block.getLocation(), leftover));
                    }
                } else {
                    block.getWorld().dropItemNaturally(block.getLocation(), drop);
                }
            });

            if (!manager.isActive(block)) {
                // La passe vient de se terminer (chunk entierement parcouru).
                Location effect = block.getLocation().add(0.5, 1.0, 0.5);
                world.spawnParticle(Particle.EXPLOSION_LARGE, effect, 3, 0.3, 0.3, 0.3);
                world.playSound(effect, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            updateHologram(block);
        }
        stale.forEach(manager::forgetMachine);
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
