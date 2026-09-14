package com.mysteriacraft.customitems.machine;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemDefinition;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Traite l'utilisation de la Machine a Transformation :
 * - clic-droit avec le carburant en main -> ravitaille la machine (ajoute des charges) ;
 * - clic-droit a vide -> affiche l'etat (carburant restant, cooldown) ;
 * - clic-droit avec un minerai accepte en main -> tente la transformation si du carburant est
 *   disponible et que le cooldown (par machine) est ecoule ; le minerai est consomme dans tous
 *   les cas, avec "chance-reussite" % de le transformer en Lucky Block. En cas d'echec, il est perdu.
 */
public class MachineService {

    private final MachineManager manager;
    private final CustomItemManager customItemManager;
    private final LuckyBlockManager luckyBlockManager;
    private final MessageManager messages;

    public MachineService(MachineManager manager, CustomItemManager customItemManager,
                           LuckyBlockManager luckyBlockManager, MessageManager messages) {
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.luckyBlockManager = luckyBlockManager;
        this.messages = messages;
    }

    public void handleInteract(Player player, Block machineBlock) {
        ItemStack inHand = player.getInventory().getItemInMainHand();

        if (inHand.getType() == Material.AIR) {
            showStatus(player, machineBlock);
            return;
        }

        if (manager.getFuelItemId().equals(customItemManager.getCustomItemId(inHand))) {
            refuel(player, machineBlock, inHand);
            return;
        }

        attemptTransformation(player, machineBlock, inHand);
    }

    private void showStatus(Player player, Block machineBlock) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("carburant", String.valueOf(manager.getFuel(machineBlock)));

        long remaining = manager.getRemainingCooldownMillis(machineBlock);
        if (remaining > 0) {
            placeholders.put("cooldown", formatDuration(remaining));
            messages.send(player, "machine.statut-en-attente", placeholders);
        } else {
            messages.send(player, "machine.statut-pret", placeholders);
        }
    }

    private void refuel(Player player, Block machineBlock, ItemStack fuelItem) {
        int remaining = fuelItem.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(fuelItem, remaining) : null);

        int newTotal = manager.addFuel(machineBlock, manager.getChargesPerFuel());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("charges", String.valueOf(manager.getChargesPerFuel()));
        placeholders.put("total", String.valueOf(newTotal));
        messages.send(player, "machine.ravitaillee", placeholders);

        Location loc = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc, 15, 0.4, 0.4, 0.4);
        player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
    }

    private void attemptTransformation(Player player, Block machineBlock, ItemStack inHand) {
        String familyId = manager.getTargetFamily(inHand.getType());
        if (familyId == null) {
            messages.send(player, "machine.minerai-non-accepte");
            return;
        }

        if (manager.getFuel(machineBlock) <= 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("carburant", customItemName());
            messages.send(player, "machine.sans-carburant", placeholders);
            return;
        }

        long remainingCooldown = manager.getRemainingCooldownMillis(machineBlock);
        if (remainingCooldown > 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("temps", formatDuration(remainingCooldown));
            messages.send(player, "machine.cooldown", placeholders);
            return;
        }

        LuckyBlockFamily family = luckyBlockManager.getFamily(familyId);
        if (family == null) {
            messages.send(player, "machine.famille-introuvable");
            return;
        }

        // Consomme exactement 1 exemplaire du minerai, 1 charge de carburant, et relance le cooldown.
        int remaining = inHand.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);
        manager.consumeCharge(machineBlock);
        manager.markUsedNow(machineBlock);

        Location effectLocation = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        boolean success = ThreadLocalRandom.current().nextDouble(100.0) < manager.getSuccessChance();

        if (success) {
            ItemStack reward = luckyBlockManager.createItem(family);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }

            effectLocation.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, effectLocation, 25, 0.4, 0.4, 0.4);
            player.playSound(effectLocation, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("caisse", family.displayName());
            messages.send(player, "machine.reussite", placeholders);
        } else {
            effectLocation.getWorld().spawnParticle(Particle.SMOKE_NORMAL, effectLocation, 20, 0.4, 0.4, 0.4);
            player.playSound(effectLocation, Sound.ENTITY_ITEM_BREAK, 1f, 0.7f);
            messages.send(player, "machine.echec");
        }
    }

    private String customItemName() {
        CustomItemDefinition definition = customItemManager.getItem(manager.getFuelItemId());
        return definition != null ? definition.displayName() : manager.getFuelItemId();
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
