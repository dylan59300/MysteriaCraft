package com.mysteriacraft.luckyblock.generator;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
 * Traite l'utilisation du Generateur de Lucky Block :
 * - clic-droit avec un carburant (voir generateur-luckyblock.carburants) en main -> l'alimente ;
 * - clic-droit a vide -> affiche son etat (famille, carburant restant) ;
 * - tickAll() periodique (voir MysteriaCraft) -> produit les Lucky Blocks dus depuis le dernier
 *   appel pour chaque generateur actif (voir LuckyBlockGeneratorManager#tick) et les depose dans
 *   le conteneur colle (ou au sol si aucun n'est colle).
 */
public class LuckyBlockGeneratorService {

    private final LuckyBlockGeneratorManager manager;
    private final CustomItemManager customItemManager;
    private final LuckyBlockManager luckyBlockManager;
    private final MessageManager messages;

    public LuckyBlockGeneratorService(LuckyBlockGeneratorManager manager, CustomItemManager customItemManager,
                                       LuckyBlockManager luckyBlockManager, MessageManager messages) {
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.luckyBlockManager = luckyBlockManager;
        this.messages = messages;
    }

    public void handleInteract(Player player, Block generatorBlock, ItemStack inHand) {
        if (inHand.getType() == Material.AIR) {
            showStatus(player, generatorBlock);
            return;
        }

        String customItemId = customItemManager.getCustomItemId(inHand);

        if (customItemId != null && customItemId.equalsIgnoreCase(manager.getRateBonusItemId())) {
            upgradeRate(player, generatorBlock, inHand);
            return;
        }
        if (customItemId != null && customItemId.equalsIgnoreCase(manager.getStorageBonusItemId())) {
            upgradeStorage(player, generatorBlock, inHand);
            return;
        }

        LuckyBlockGeneratorManager.FuelType fuelType = manager.getFuelType(customItemId);
        if (fuelType == null) {
            messages.send(player, "generateurlb.carburant-invalide");
            return;
        }

        int remaining = inHand.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);
        int added = manager.refuel(generatorBlock, fuelType.production());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("production", String.valueOf(added));
        placeholders.put("total", String.valueOf(manager.getFuel(generatorBlock)));
        placeholders.put("max", String.valueOf(manager.getEffectiveFuelMax(generatorBlock)));
        messages.send(player, added < fuelType.production() ? "generateurlb.ravitaille-plafonne" : "generateurlb.ravitaille", placeholders);

        Location loc = generatorBlock.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc, 15, 0.4, 0.4, 0.4);
        player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
        updateHologram(generatorBlock);
    }

    private void upgradeRate(Player player, Block generatorBlock, ItemStack boostItem) {
        double currentBonus = manager.getBonusRythme(generatorBlock);
        if (currentBonus >= manager.getRateBonusMax()) {
            messages.send(player, "generateurlb.boost-rythme-max");
            return;
        }
        int remaining = boostItem.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(boostItem, remaining) : null);

        double newBonus = manager.addBonusRythme(generatorBlock, 10.0);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("bonus", formatBonus(newBonus));
        placeholders.put("intervalle", String.valueOf(manager.getEffectiveIntervalSeconds(generatorBlock)));
        messages.send(player, "generateurlb.boost-rythme-effectue", placeholders);

        Location loc = generatorBlock.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.5, 0.3, 0.02);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
        updateHologram(generatorBlock);
    }

    private void upgradeStorage(Player player, Block generatorBlock, ItemStack boostItem) {
        double currentBonus = manager.getBonusStockage(generatorBlock);
        if (currentBonus >= manager.getStorageBonusMax()) {
            messages.send(player, "generateurlb.boost-stockage-max");
            return;
        }
        int remaining = boostItem.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(boostItem, remaining) : null);

        double newBonus = manager.addBonusStockage(generatorBlock, 20.0);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("bonus", formatBonus(newBonus));
        placeholders.put("max", String.valueOf(manager.getEffectiveFuelMax(generatorBlock)));
        messages.send(player, "generateurlb.boost-stockage-effectue", placeholders);

        Location loc = generatorBlock.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.5, 0.3, 0.02);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
        updateHologram(generatorBlock);
    }

    private void showStatus(Player player, Block generatorBlock) {
        LuckyBlockFamily family = manager.getFamily(generatorBlock);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("famille", family != null ? family.displayName() : "?");
        placeholders.put("carburant", String.valueOf(manager.getFuel(generatorBlock)));
        placeholders.put("max", String.valueOf(manager.getEffectiveFuelMax(generatorBlock)));
        placeholders.put("intervalle", String.valueOf(manager.getEffectiveIntervalSeconds(generatorBlock)));
        messages.send(player, "generateurlb.statut", placeholders);
    }

    private String formatBonus(double bonus) {
        return bonus == Math.floor(bonus) ? String.valueOf((int) bonus) : String.valueOf(bonus);
    }

    /** Appele periodiquement (voir MysteriaCraft) pour tous les generateurs actifs : produit les
     * Lucky Blocks dus depuis le dernier appel et les depose dans le conteneur colle (ou au sol). */
    public void tickAll(Iterable<Location> locations) {
        List<Location> stale = new ArrayList<>();
        for (Location location : locations) {
            World world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            Block block = location.getBlock();
            if (!manager.isGeneratorBlock(block)) {
                stale.add(location);
                continue;
            }
            int produced = manager.tick(block);
            if (produced > 0) {
                depositProduction(block, produced);
            }
            updateHologram(block);
        }
        stale.forEach(manager::forgetGenerator);
    }

    private void depositProduction(Block block, int amount) {
        LuckyBlockFamily family = manager.getFamily(block);
        if (family == null) {
            return;
        }
        ItemStack reward = luckyBlockManager.createItem(family, amount);
        Block containerBlock = manager.getOutputContainer(block);
        if (containerBlock != null && containerBlock.getState() instanceof Container container) {
            Inventory inventory = container.getInventory();
            Map<Integer, ItemStack> leftovers = inventory.addItem(reward);
            leftovers.values().forEach(leftover ->
                    containerBlock.getWorld().dropItemNaturally(containerBlock.getLocation(), leftover));
        } else {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 1, 0.5), reward);
        }
        Location effect = block.getLocation().add(0.5, 1.2, 0.5);
        effect.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, effect, 15, 0.3, 0.3, 0.3);
    }

    private void updateHologram(Block block) {
        ArmorStand stand = manager.getHologram(block);
        if (stand == null) {
            return;
        }
        LuckyBlockFamily family = manager.getFamily(block);
        String name = family != null ? family.displayName() : "?";
        stand.setCustomName(MessageManager.color("&d&lGenerateur &7[" + name + "&7] &7- &b"
                + manager.getFuel(block) + " &7carburant"));
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
