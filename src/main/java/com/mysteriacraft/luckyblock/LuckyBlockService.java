package com.mysteriacraft.luckyblock;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.economy.EconomyManager;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestre la casse d'un Lucky Block : tirage pondere (aucun cooldown, se recasse immediatement)
 * et application de l'effet (bon via RewardGiver, mauvais via TNT/mobs/potion/foudre).
 * Gere aussi l'achat direct (/luckyblock buy) via la monnaie interne.
 *
 * IMPORTANT : tout est traite de maniere SYNCHRONE, dans le handler d'evenement lui-meme.
 * BlockBreakEvent#setCancelled()/setDropItems() n'ont aucun effet si on les appelle apres que
 * l'evenement a fini d'etre traite (ex: depuis un Bukkit.getScheduler().runTaskAsynchronously()
 * puis un runTask() planifie pour plus tard) : Bukkit a deja casse le bloc et applique ses drops
 * par defaut avant que ce code differe ne s'execute. C'est ce qui causait a la fois le Lucky Block
 * qui se cassait quand meme pendant son cooldown et le drop du bloc vanilla brut (GOLD_BLOCK...)
 * au lieu de l'effet attendu.
 */
public class LuckyBlockService implements RewardGiver.LuckyBlockGiveHandler {

    private final LuckyBlockManager manager;
    private final EconomyManager economyManager;
    private final RewardGiver rewardGiver;
    private final MessageManager messages;

    public LuckyBlockService(LuckyBlockManager manager, EconomyManager economyManager,
                              RewardGiver rewardGiver, MessageManager messages) {
        this.manager = manager;
        this.economyManager = economyManager;
        this.rewardGiver = rewardGiver;
        this.messages = messages;
    }

    /** Appele par le listener sur BlockBreakEvent quand le bloc casse est un Lucky Block marque. */
    public void handleBreak(BlockBreakEvent event, LuckyBlockFamily family) {
        Player player = event.getPlayer();
        double bonusPercent = manager.getBonus(event.getBlock());

        LuckyBlockEffect effect = manager.pickEffect(family, bonusPercent);

        event.setDropItems(false);
        manager.untagBlock(event.getBlock());
        if (effect == null) {
            messages.send(player, "luckyblock.aucun-effet");
            return;
        }
        applyEffect(player, event.getBlock().getLocation(), effect);
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

    /** Donne un Lucky Block gratuitement (recompense de battlepass/quete/autre Lucky Block). */
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
            messages.send(player, "general.inventaire-plein");
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
        if (!family.isActiveNow()) {
            messages.send(player, "luckyblock.hors-saison");
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
            messages.send(player, "general.inventaire-plein");
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(quantity));
        placeholders.put("famille", family.displayName());
        placeholders.put("prix", economyManager.format(totalPrice));
        messages.send(player, "luckyblock.achat-reussi", placeholders);
    }
}
