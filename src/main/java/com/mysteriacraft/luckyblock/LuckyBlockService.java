package com.mysteriacraft.luckyblock;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestre la casse d'un Lucky Block : cooldown par joueur/famille (annule la casse tant qu'il
 * n'est pas ecoule, pour que le bloc reste utilisable plus tard), puis tirage pondere et
 * application de l'effet (bon via RewardGiver, mauvais via TNT/mobs/potion/foudre).
 * Gere aussi l'achat direct (/luckyblock buy) via la monnaie interne.
 */
public class LuckyBlockService implements RewardGiver.LuckyBlockGiveHandler {

    private final Plugin plugin;
    private final LuckyBlockManager manager;
    private final EconomyManager economyManager;
    private final RewardGiver rewardGiver;
    private final MessageManager messages;

    public LuckyBlockService(Plugin plugin, LuckyBlockManager manager, EconomyManager economyManager,
                              RewardGiver rewardGiver, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.economyManager = economyManager;
        this.rewardGiver = rewardGiver;
        this.messages = messages;
    }

    /** Appele par le listener sur BlockBreakEvent quand le bloc casse est un Lucky Block marque. */
    public void handleBreak(BlockBreakEvent event, LuckyBlockFamily family) {
        Player player = event.getPlayer();
        // Lu maintenant : une fois le bloc reellement casse (event non annule), on desenregistre
        // sa position (voir LuckyBlockManager#untagBlock) et son bonus de minerais serait perdu.
        double bonusPercent = manager.getBonus(event.getBlock());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long lastUsed = manager.getLastUsed(player.getUniqueId(), family.id());
            long remainingMillis = (lastUsed + family.cooldownSeconds() * 1000L) - System.currentTimeMillis();

            if (lastUsed > 0 && remainingMillis > 0) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Le bloc reste intact tant que le cooldown n'est pas ecoule.
                    event.setCancelled(true);
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("temps", formatDuration(remainingMillis));
                    messages.send(player, "luckyblock.cooldown", placeholders);
                });
                return;
            }

            manager.markUsed(player.getUniqueId(), family.id());
            LuckyBlockEffect effect = manager.pickEffect(family, bonusPercent);

            Bukkit.getScheduler().runTask(plugin, () -> {
                event.setDropItems(false);
                manager.untagBlock(event.getBlock());
                if (effect == null) {
                    messages.send(player, "luckyblock.aucun-effet");
                    return;
                }
                applyEffect(player, event.getBlock().getLocation(), effect);
            });
        });
    }

    private void applyEffect(Player player, Location location, LuckyBlockEffect effect) {
        if (effect.kind() == EffectKind.BON) {
            rewardGiver.give(player, effect.reward());
            location.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, location.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5);
            player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("effet", effect.reward().displayName());
            messages.send(player, "luckyblock.bon-effet", placeholders);
            return;
        }

        location.getWorld().spawnParticle(Particle.SMOKE_NORMAL, location.clone().add(0.5, 0.5, 0.5), 40, 0.5, 0.5, 0.5);
        player.playSound(location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);

        switch (effect.badType()) {
            case TNT -> spawnTnt(location, effect.intValue());
            case MOBS -> spawnMobs(location, effect.stringValue(), effect.intValue());
            case POTION -> applyPotion(player, effect.stringValue(), effect.intValue(), effect.amplifier());
            case FOUDRE -> location.getWorld().strikeLightning(location);
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("effet", effect.displayName());
        messages.send(player, "luckyblock.mauvais-effet", placeholders);
    }

    private void spawnTnt(Location location, int amount) {
        for (int i = 0; i < amount; i++) {
            Entity entity = location.getWorld().spawnEntity(location.clone().add(0.5, 0.5, 0.5), EntityType.PRIMED_TNT);
            if (entity instanceof TNTPrimed tnt) {
                tnt.setFuseTicks(60);
            }
        }
    }

    private void spawnMobs(Location location, String mobTypeRaw, int amount) {
        EntityType type;
        try {
            type = EntityType.valueOf(mobTypeRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = EntityType.ZOMBIE;
        }
        for (int i = 0; i < amount; i++) {
            Entity entity = location.getWorld().spawnEntity(location.clone().add(0.5, 0.5, 0.5), type);
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.setRemoveWhenFarAway(true);
            }
        }
    }

    private void applyPotion(Player player, String potionTypeRaw, int durationTicks, int amplifier) {
        PotionEffectType type = PotionEffectType.getByName(potionTypeRaw.toUpperCase());
        if (type == null) {
            type = PotionEffectType.POISON;
        }
        player.addPotionEffect(new PotionEffect(type, Math.max(20, durationTicks), Math.max(0, amplifier)));
    }

    /** Donne un Lucky Block gratuitement (recompense de crate/battlepass/quete/autre Lucky Block). */
    @Override
    public void giveLuckyBlock(Player player, String familyId) {
        LuckyBlockFamily family = manager.getFamily(familyId);
        if (family == null) {
            return;
        }
        ItemStack item = manager.createItem(family);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            messages.send(player, "kits.inventaire-plein");
        }
    }

    /** Achete l'item Lucky Block d'une famille avec la monnaie interne. */
    public void buy(Player player, String familyId, int quantity) {
        LuckyBlockFamily family = manager.getFamily(familyId);
        if (family == null) {
            messages.send(player, "luckyblock.introuvable");
            return;
        }
        if (!family.isPurchasable()) {
            messages.send(player, "luckyblock.non-achetable");
            return;
        }

        double totalPrice = family.buyPrice() * quantity;
        if (!economyManager.has(player.getUniqueId(), totalPrice) || !economyManager.withdraw(player.getUniqueId(), totalPrice)) {
            messages.send(player, "luckyblock.fonds-insuffisants");
            return;
        }

        ItemStack item = manager.createItem(family, quantity);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            messages.send(player, "kits.inventaire-plein");
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(quantity));
        placeholders.put("famille", family.displayName());
        placeholders.put("prix", economyManager.format(totalPrice));
        messages.send(player, "luckyblock.achat-reussi", placeholders);
    }

    /** Formate une duree en millisecondes en "XhYmZs" (n'affiche que les unites non nulles). */
    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
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
