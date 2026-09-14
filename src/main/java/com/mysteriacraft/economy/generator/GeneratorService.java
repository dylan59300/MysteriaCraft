package com.mysteriacraft.economy.generator;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traite l'utilisation des Generateurs d'Argent :
 * - clic-droit dessus -> recupere tout l'argent accumule depuis la derniere recuperation
 *   (ou depuis la pose), credite directement sur le solde du joueur (module Economie) ;
 * - le stock est aussi recalcule/affiche periodiquement (voir tickGenerators, appele depuis
 *   MysteriaCraft) pour tenir l'hologramme a jour meme sans interaction ;
 * - casser un generateur recupere automatiquement son stock au joueur qui l'a casse avant de
 *   le faire tomber en item (rien n'est jamais perdu).
 */
public class GeneratorService {

    private final GeneratorManager manager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public GeneratorService(GeneratorManager manager, EconomyManager economyManager, MessageManager messages) {
        this.manager = manager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public void handleInteract(Player player, Block block) {
        GeneratorManager.GeneratorType type = manager.getBlockType(block);
        if (type == null) {
            messages.send(player, "generateur.type-invalide");
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

        economyManager.deposit(player.getUniqueId(), collected);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("montant", economyManager.format(collected));
        placeholders.put("generateur", type.displayName());
        messages.send(player, "generateur.recupere", placeholders);

        Location loc = block.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc, 20, 0.4, 0.4, 0.4);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        updateHologram(block);
    }

    /** A appeler quand un generateur est casse : recupere automatiquement son stock pour le joueur
     * qui l'a casse (rien n'est perdu), avant que le bloc ne tombe en item. */
    public void collectOnBreak(Player breaker, Block block) {
        double collected = manager.collect(block);
        if (collected <= 0 || breaker == null) {
            return;
        }
        economyManager.deposit(breaker.getUniqueId(), collected);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("montant", economyManager.format(collected));
        messages.send(breaker, "generateur.recupere-a-la-casse", placeholders);
    }

    /** Recalcule le stock de tous les generateurs actifs et met a jour leur hologramme. Appele
     * periodiquement depuis MysteriaCraft (generateurs.yml: tick-secondes). */
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
            updateHologram(block);
        }
        stale.forEach(manager::forgetGenerator);
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
        String text = type.displayName() + " &7| " + bar + " &7| &a" + economyManager.format(stored)
                + " &7/ &a" + economyManager.format(type.storageMax());
        stand.setCustomName(MessageManager.color(text));
    }
}
