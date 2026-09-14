package com.mysteriacraft.economy.generator;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Traite l'utilisation des Generateurs d'Argent :
 * - clic-droit a vide (ou avec tout objet non reconnu) -> recupere tout l'argent accumule,
 *   credite directement sur le solde du joueur (module Economie), taxe de recuperation deduite ;
 * - clic-droit avec l'objet "boost" en main -> augmente durablement le rythme de generation de
 *   CE generateur (independant de son type de base) ;
 * - un hopper colle a une face active l'auto-collecte : l'argent genere est credite en continu
 *   au proprietaire sans intervention, a chaque tick (voir tickGenerators) ;
 * - casser un generateur recupere automatiquement son stock au joueur qui l'a casse avant de
 *   le faire tomber en item (rien n'est jamais perdu).
 */
public class GeneratorService implements RewardGiver.GeneratorGiveHandler {

    private final GeneratorManager manager;
    private final CustomItemManager customItemManager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public GeneratorService(GeneratorManager manager, CustomItemManager customItemManager,
                             EconomyManager economyManager, MessageManager messages) {
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public void handleInteract(Player player, Block block) {
        GeneratorManager.GeneratorType type = manager.getBlockType(block);
        if (type == null) {
            messages.send(player, "generateur.type-invalide");
            return;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        String customItemId = customItemManager.getCustomItemId(inHand);
        if (customItemId != null && customItemId.equalsIgnoreCase(manager.getUpgradeItemId())) {
            upgrade(player, block, inHand);
            return;
        }

        double collected = manager.collect(block);
        if (collected <= 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("generateur", type.displayName());
            messages.send(player, "generateur.rien-a-recuperer", placeholders);
            updateHologram(block);
            return;
        }

        double net = depositWithTax(player.getUniqueId(), collected);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("montant", economyManager.format(net));
        placeholders.put("generateur", type.displayName());
        if (manager.getTaxPercent() > 0) {
            placeholders.put("taxe", formatPercent(manager.getTaxPercent()));
            messages.send(player, "generateur.recupere-taxe", placeholders);
        } else {
            messages.send(player, "generateur.recupere", placeholders);
        }

        Location loc = block.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc, 20, 0.4, 0.4, 0.4);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        updateHologram(block);
    }

    private void upgrade(Player player, Block block, ItemStack boostItem) {
        double currentBonus = manager.getBonusPercent(block);
        if (currentBonus >= manager.getBonusMaxPercent()) {
            messages.send(player, "generateur.boost-max");
            return;
        }

        int remaining = boostItem.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(boostItem, remaining) : null);

        double newBonus = manager.addBonusPercent(block, manager.getBonusPerUpgradePercent());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("bonus", formatPercent(manager.getBonusPerUpgradePercent()));
        placeholders.put("bonus-total", formatPercent(newBonus));
        placeholders.put("bonus-max", formatPercent(manager.getBonusMaxPercent()));
        messages.send(player, "generateur.boost-effectue", placeholders);

        Location loc = block.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.5, 0.3, 0.02);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
        updateHologram(block);
    }

    /** A appeler quand un generateur est casse : recupere automatiquement son stock pour le joueur
     * qui l'a casse (rien n'est perdu), avant que le bloc ne tombe en item. */
    public void collectOnBreak(Player breaker, Block block) {
        double collected = manager.collect(block);
        if (collected <= 0 || breaker == null) {
            return;
        }
        double net = depositWithTax(breaker.getUniqueId(), collected);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("montant", economyManager.format(net));
        if (manager.getTaxPercent() > 0) {
            placeholders.put("taxe", formatPercent(manager.getTaxPercent()));
            messages.send(breaker, "generateur.recupere-a-la-casse-taxe", placeholders);
        } else {
            messages.send(breaker, "generateur.recupere-a-la-casse", placeholders);
        }
    }

    /** Donne un Generateur d'Argent (recompense de battlepass/quete/luckyblock). */
    @Override
    public void giveGenerator(Player player, String generatorTypeId, int amount) {
        GeneratorManager.GeneratorType type = manager.getType(generatorTypeId);
        if (type == null) {
            return;
        }
        ItemStack item = manager.createGeneratorItem(type, amount);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    /** Recalcule le stock de tous les generateurs actifs, applique l'auto-collecte (hopper colle)
     * et met a jour leur hologramme. Appele periodiquement depuis MysteriaCraft (generateurs.yml). */
    public void tickGenerators() {
        List<Location> stale = new ArrayList<>();
        for (Location location : manager.getActiveGeneratorLocations()) {
            World world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            Block block = location.getBlock();
            if (!manager.isGeneratorBlock(block)) {
                stale.add(location);
                continue;
            }
            manager.accrue(block);

            if (manager.getAdjacentHopper(block) != null) {
                autoCollect(block);
            }
            updateHologram(block);
        }
        stale.forEach(manager::forgetGenerator);
    }

    /** Auto-collecte silencieuse : credite directement le proprietaire sans clic-droit, des qu'un
     * hopper est colle a la machine. Aucune monnaie physique n'existe dans ce plugin : le hopper
     * sert simplement de detecteur qui active le depot automatique sur le solde du proprietaire. */
    private void autoCollect(Block block) {
        UUID owner = manager.getOwner(block);
        if (owner == null) {
            return;
        }
        double stored = manager.getStored(block);
        if (stored <= 0) {
            return;
        }
        manager.setStored(block, 0.0);
        depositWithTax(owner, stored);
    }

    private double depositWithTax(UUID uuid, double gross) {
        double net = gross * (1.0 - manager.getTaxPercent() / 100.0);
        economyManager.deposit(uuid, net);
        return net;
    }

    private void updateHologram(Block block) {
        ArmorStand stand = manager.getHologram(block);
        if (stand == null) {
            return;
        }
        GeneratorManager.GeneratorType type = manager.getBlockType(block);
        if (type == null) {
            return;
        }

        double stored = manager.getStored(block);
        int segments = 10;
        double progress = type.storageMax() > 0 ? Math.min(1.0, stored / type.storageMax()) : 1.0;
        int filled = Math.max(0, Math.min(segments, (int) Math.round(progress * segments)));
        boolean full = filled >= segments;

        String bar = "&f[" + (full ? "&a" : "&e") + "■".repeat(filled) + "&7" + "□".repeat(segments - filled) + "&f]";
        double bonus = manager.getBonusPercent(block);
        String bonusSuffix = bonus > 0 ? " &7(&d+" + formatPercent(bonus) + "&7)" : "";
        String autoSuffix = manager.getAdjacentHopper(block) != null ? " &7| &b[AUTO]" : "";

        String text = type.displayName() + bonusSuffix + " &7| " + bar + " &7| &a" + economyManager.format(stored)
                + " &7/ &a" + economyManager.format(type.storageMax()) + autoSuffix;
        stand.setCustomName(MessageManager.color(text));
    }

    private String formatPercent(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
