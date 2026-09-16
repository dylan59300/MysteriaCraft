package com.mysteriacraft.luckyblock;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestre l'utilisation d'un Lucky Block pose : clic-droit dessus (aucun cooldown, reutilisable
 * immediatement, le bloc n'est PAS casse) tire un effet pondere et l'applique (bon via RewardGiver,
 * mauvais via TNT/mobs/potion/foudre). Gere aussi l'achat direct (/luckyblock buy) via la monnaie
 * interne.
 */
public class LuckyBlockService implements RewardGiver.LuckyBlockGiveHandler {

    /** Plafond d'utilisations par appel de /luckyblockadmin simulate, pour eviter qu'un admin ne
     * declenche par erreur des centaines d'effets MAUVAIS reels (TNT, mobs...) d'un coup. */
    private static final int MAX_SIMULATION = 200;

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

    /** Appele par le listener sur un clic-droit sur un Lucky Block pose et marque : tire un effet
     * et l'applique. Le bloc reste en place et reste utilisable immediatement (pas de cooldown). */
    public void handleRightClick(Player player, Block block, LuckyBlockFamily family) {
        double bonusPercent = manager.getBonus(block);
        LuckyBlockEffect effect = manager.pickEffect(player.getUniqueId(), family, bonusPercent);
        if (effect == null) {
            messages.send(player, "luckyblock.aucun-effet");
            return;
        }
        applyEffect(player, block.getLocation(), effect);
    }

    private void applyEffect(Player player, Location location, LuckyBlockEffect effect) {
        applyEffect(player, location, effect, true);
    }

    /** @param announce si false, donne/applique l'effet REELLEMENT (items, argent, TNT, mobs...)
     *                  sans envoyer le message de chat par casse ni jouer les sons/particules :
     *                  utilise par simulate() pour eviter le spam sur un grand nombre de casses. */
    private void applyEffect(Player player, Location location, LuckyBlockEffect effect, boolean announce) {
        if (effect.kind() == EffectKind.BON) {
            rewardGiver.give(player, effect.reward());
            if (announce) {
                location.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, location.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5);
                player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("effet", effect.reward().displayName());
                messages.send(player, "luckyblock.bon-effet", placeholders);
            }
            return;
        }

        if (announce) {
            location.getWorld().spawnParticle(Particle.SMOKE_NORMAL, location.clone().add(0.5, 0.5, 0.5), 40, 0.5, 0.5, 0.5);
            player.playSound(location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
        }

        switch (effect.badType()) {
            case TNT -> spawnTnt(location, effect.intValue());
            case MOBS -> spawnMobs(location, effect.stringValue(), effect.intValue());
            case POTION -> applyPotion(player, effect.stringValue(), effect.intValue(), effect.amplifier());
            case FOUDRE -> location.getWorld().strikeLightning(location);
        }

        if (announce) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("effet", effect.displayName());
            messages.send(player, "luckyblock.mauvais-effet", placeholders);
        }
    }

    /**
     * Outil admin (/luckyblockadmin simulate) : rejoue REELLEMENT count casses de cette famille
     * pour cet admin (mêmes effets qu'une vraie casse : items/argent recus, TNT/mobs si un effet
     * MAUVAIS existe dans cette famille), sans le spam d'un message par casse, puis affiche un
     * recapitulatif du nombre de fois obtenu par effet. Plafonne a MAX_SIMULATION.
     */
    public void simulate(Player admin, LuckyBlockFamily family, int count) {
        int total = Math.max(1, Math.min(count, MAX_SIMULATION));
        Map<String, Integer> tally = new LinkedHashMap<>();
        int aucunEffet = 0;

        for (int i = 0; i < total; i++) {
            LuckyBlockEffect effect = manager.pickEffect(admin.getUniqueId(), family, 0);
            if (effect == null) {
                aucunEffet++;
                continue;
            }
            applyEffect(admin, admin.getLocation(), effect, false);
            tally.merge(effect.displayName(), 1, Integer::sum);
        }

        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("nombre", String.valueOf(total));
        titlePlaceholders.put("famille", family.displayName());
        messages.send(admin, "luckyblock.simulation-titre", titlePlaceholders);
        tally.forEach((name, obtained) -> admin.sendMessage(MessageManager.color(
                "&7- &e" + name + " &7x&a" + obtained + " &7(&e" + String.format("%.1f", 100.0 * obtained / total) + "%&7)")));
        if (aucunEffet > 0) {
            admin.sendMessage(MessageManager.color("&7- &c(aucun effet configure) &7x&c" + aucunEffet));
        }
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
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("ids", manager.getFamiliesSorted().stream().map(LuckyBlockFamily::id).collect(Collectors.joining(", ")));
            messages.send(player, "luckyblock.introuvable", placeholders);
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
