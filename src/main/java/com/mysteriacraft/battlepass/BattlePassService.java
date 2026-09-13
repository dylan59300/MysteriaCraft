package com.mysteriacraft.battlepass;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestre la progression du BattlePass : gain d'xp (avec detection de passage de niveau),
 * achat de la piste premium (avec confirmation cliquable), et reclamation des recompenses
 * (gratuite/premium) par palier.
 */
public class BattlePassService implements RewardGiver.XpBoosterHandler {

    public static final String TRACK_FREE = "GRATUIT";
    public static final String TRACK_PREMIUM = "PREMIUM";

    /** Permission accordant le premium sans achat (ex: liee a un rang ou un kit VIP vendu ailleurs). */
    public static final String PERMISSION_PREMIUM = "mysteriacraft.battlepass.premium";

    private static final long CONFIRMATION_EXPIRATION_SECONDS = 30L;

    private final Plugin plugin;
    private final BattlePassManager manager;
    private final EconomyManager economyManager;
    private final RewardGiver rewardGiver;
    private final MessageManager messages;

    /** Achats de premium en attente de confirmation cliquable, par joueur (expiration en millis). */
    private final Map<UUID, Long> pendingPremiumPurchase = new ConcurrentHashMap<>();

    /**
     * Boosts d'xp actifs (multiplicateur + expiration en millis), par joueur. Volontairement
     * en memoire uniquement (non persiste) : un redemarrage du serveur y met fin, ce qui est
     * un compromis acceptable pour un bonus temporaire.
     */
    private final Map<UUID, ActiveBooster> activeBoosters = new ConcurrentHashMap<>();

    private record ActiveBooster(double multiplier, long expiresAtMillis) {
        boolean isActive() {
            return System.currentTimeMillis() < expiresAtMillis;
        }
    }

    public BattlePassService(Plugin plugin, BattlePassManager manager, EconomyManager economyManager,
                              RewardGiver rewardGiver, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.economyManager = economyManager;
        this.rewardGiver = rewardGiver;
        this.messages = messages;
    }

    /** Active un boost d'xp temporaire (depuis une recompense de type BOOST_XP, quel que soit le module source). */
    @Override
    public void activateBooster(Player player, long durationSeconds, double multiplier) {
        long expiresAt = System.currentTimeMillis() + durationSeconds * 1000L;
        activeBoosters.put(player.getUniqueId(), new ActiveBooster(multiplier, expiresAt));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("multiplicateur", String.valueOf(multiplier));
        placeholders.put("duree", String.valueOf(durationSeconds / 60));
        messages.send(player, "battlepass.booster-active", placeholders);
    }

    private double currentMultiplier(UUID uuid) {
        ActiveBooster booster = activeBoosters.get(uuid);
        if (booster != null && booster.isActive()) {
            return booster.multiplier();
        }
        activeBoosters.remove(uuid);
        return 1.0;
    }

    /** Premium effectif : achete OU accorde par permission (rang, kit VIP vendu via un autre systeme). */
    public boolean isPremiumEffective(Player player) {
        return player.hasPermission(PERMISSION_PREMIUM) || manager.isPremium(player.getUniqueId());
    }

    /** Ajoute de l'xp a un joueur en ligne (multipliee par un eventuel boost actif) et le previent s'il passe un ou plusieurs niveaux. */
    public void addXp(Player player, long amount) {
        long boostedAmount = Math.round(amount * currentMultiplier(player.getUniqueId()));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long oldXp = manager.getXp(player.getUniqueId());
            int oldLevel = manager.computeLevel(oldXp);
            long newXp = manager.addXp(player.getUniqueId(), boostedAmount);
            int newLevel = manager.computeLevel(newXp);

            if (newLevel > oldLevel) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("niveau", String.valueOf(newLevel));
                    messages.send(player, "battlepass.niveau-suivant", placeholders);
                });
            }
        });
    }

    /** Etape 1 : demande de confirmation avant l'achat du premium (comme le seuil de confirmation sur /pay). */
    public void requestPremiumPurchase(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (isPremiumEffective(player)) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "battlepass.deja-premium"));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                long expiresAt = System.currentTimeMillis() + CONFIRMATION_EXPIRATION_SECONDS * 1000L;
                pendingPremiumPurchase.put(player.getUniqueId(), expiresAt);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("prix", economyManager.format(manager.getPremiumPrice()));
                messages.send(player, "battlepass.confirmation-demande", placeholders);

                String clicText = messages.raw("battlepass.confirmation-clic")
                        .replace("{expiration}", String.valueOf(CONFIRMATION_EXPIRATION_SECONDS));
                String survolText = messages.raw("battlepass.confirmation-survol");

                LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
                Component clicComponent = legacy.deserialize(clicText)
                        .clickEvent(ClickEvent.runCommand("/battlepassconfirmpremium"))
                        .hoverEvent(HoverEvent.showText(legacy.deserialize(survolText)));
                player.sendMessage(clicComponent);
            });
        });
    }

    /** Etape 2 : confirmation effective, execute l'achat si toujours valide. */
    public void confirmPremiumPurchase(Player player) {
        Long expiresAt = pendingPremiumPurchase.remove(player.getUniqueId());
        if (expiresAt == null || System.currentTimeMillis() > expiresAt) {
            messages.send(player, "battlepass.confirmation-expiree");
            return;
        }

        double price = manager.getPremiumPrice();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (isPremiumEffective(player)) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "battlepass.deja-premium"));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!economyManager.has(player.getUniqueId(), price) || !economyManager.withdraw(player.getUniqueId(), price)) {
                    messages.send(player, "battlepass.fonds-insuffisants");
                    return;
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    manager.setPremium(player.getUniqueId(), true);
                    Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "battlepass.premium-active"));
                });
            });
        });
    }

    public void claimReward(Player player, int level, String track, Runnable onFinished) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long xp = manager.getXp(player.getUniqueId());
            int currentLevel = manager.computeLevel(xp);
            BattlePassLevel bpLevel = manager.getLevel(level);
            boolean premiumEffective = isPremiumEffective(player);

            Runnable fail = () -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (onFinished != null) {
                    onFinished.run();
                }
            });

            if (bpLevel == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "battlepass.introuvable"));
                fail.run();
                return;
            }
            if (level > currentLevel) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "battlepass.niveau-verrouille"));
                fail.run();
                return;
            }

            BattlePassReward reward = TRACK_PREMIUM.equals(track) ? bpLevel.premiumReward() : bpLevel.freeReward();
            if (reward == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "battlepass.aucune-recompense"));
                fail.run();
                return;
            }
            if (TRACK_PREMIUM.equals(track) && !premiumEffective) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "battlepass.besoin-premium"));
                fail.run();
                return;
            }
            if (manager.hasClaimed(player.getUniqueId(), level, track)) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "battlepass.deja-reclame"));
                fail.run();
                return;
            }

            manager.markClaimed(player.getUniqueId(), level, track);
            Bukkit.getScheduler().runTask(plugin, () -> {
                giveReward(player, reward);
                if (onFinished != null) {
                    onFinished.run();
                }
            });
        });
    }

    private void giveReward(Player player, BattlePassReward reward) {
        rewardGiver.give(player, reward.reward());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("recompense", reward.displayName());
        messages.send(player, "battlepass.recompense-recue", placeholders);
    }
}
