package com.mysteriacraft.crates;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.crates.gui.InstantCrateGui;
import com.mysteriacraft.crates.gui.QuickRevealCrateGui;
import com.mysteriacraft.crates.gui.RouletteCrateGui;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestre l'ouverture d'une caisse : permission, verrou anti-double-ouverture, consommation
 * d'une cle virtuelle (async), garantie "pity", tirage(s) pondere(s), puis lance l'animation GUI.
 */
public class CrateService {

    private final Plugin plugin;
    private final CrateManager crateManager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    /** Joueurs ayant actuellement une animation d'ouverture en cours (anti-spam/anti-exploit). */
    private final Set<UUID> currentlyOpening = ConcurrentHashMap.newKeySet();

    public CrateService(Plugin plugin, CrateManager crateManager, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.crateManager = crateManager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public boolean isOpening(UUID uuid) {
        return currentlyOpening.contains(uuid);
    }

    /** A appeler par les GUI d'animation une fois l'ouverture terminee (ou annulee) pour liberer le verrou. */
    public void finishOpening(UUID uuid) {
        currentlyOpening.remove(uuid);
    }

    public void open(Player player, String crateId) {
        Crate crate = crateManager.getCrate(crateId);
        if (crate == null) {
            messages.send(player, "crates.introuvable");
            return;
        }

        if (crate.hasPermissionRequirement() && !player.hasPermission(crate.permission())) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("caisse", crate.displayName());
            messages.send(player, "crates.pas-permission", placeholders);
            return;
        }

        if (!currentlyOpening.add(player.getUniqueId())) {
            messages.send(player, "crates.deja-en-cours");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean consumed = crateManager.consumeKey(player.getUniqueId(), crate.id());
            if (!consumed) {
                currentlyOpening.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("caisse", crate.displayName());
                    messages.send(player, "crates.pas-de-cle", placeholders);
                });
                return;
            }

            boolean forceLegendary = crate.hasPity()
                    && crateManager.getPityCount(player.getUniqueId(), crate.id()) >= crate.pityThreshold();
            List<CrateReward> rewards = crateManager.pickRewards(crate, forceLegendary);

            if (crate.hasPity()) {
                boolean gotLegendary = rewards.stream().anyMatch(r -> r.rarity() == Rarity.LEGENDAIRE);
                if (gotLegendary) {
                    crateManager.resetPity(player.getUniqueId(), crate.id());
                } else {
                    crateManager.incrementPity(player.getUniqueId(), crate.id());
                }
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (rewards.isEmpty()) {
                    currentlyOpening.remove(player.getUniqueId());
                    messages.send(player, "crates.aucune-recompense");
                    return;
                }
                launchAnimation(player, crate, rewards);
            });
        });
    }

    /** Achete des cles virtuelles avec la monnaie interne (via le GUI, shift-clic sur une caisse). */
    public void buyKeys(Player player, Crate crate, int quantity) {
        if (!crate.isPurchasable()) {
            messages.send(player, "crates.non-achetable");
            return;
        }
        double totalPrice = crate.keyPrice() * quantity;
        if (!economyManager.has(player.getUniqueId(), totalPrice)) {
            messages.send(player, "crates.fonds-insuffisants");
            return;
        }
        if (!economyManager.withdraw(player.getUniqueId(), totalPrice)) {
            messages.send(player, "crates.fonds-insuffisants");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int newTotal = crateManager.addKeys(player.getUniqueId(), crate.id(), quantity);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("quantite", String.valueOf(quantity));
                placeholders.put("caisse", crate.displayName());
                placeholders.put("prix", economyManager.format(totalPrice));
                placeholders.put("total", String.valueOf(newTotal));
                messages.send(player, "crates.achat-reussi", placeholders);
            });
        });
    }

    private void launchAnimation(Player player, Crate crate, List<CrateReward> rewards) {
        switch (crate.animation()) {
            case ROULETTE -> new RouletteCrateGui(plugin, player, crate, rewards, crateManager, this, messages).open();
            case QUICK_REVEAL -> new QuickRevealCrateGui(plugin, player, crate, rewards, crateManager, this, messages).open();
            case INSTANT -> new InstantCrateGui(player, crate, rewards, this, messages).open();
        }
    }

    /** Applique reellement une recompense (objet ou credit d'economie) et previent le joueur. */
    public void giveReward(Player player, CrateReward reward) {
        RewardGiver.give(player, reward.reward(), economyManager, messages);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("recompense", reward.displayName());
        messages.send(player, "crates.gain", placeholders);
    }

    /** Applique une liste de recompenses (tirages multiples) en une seule fois. */
    public void giveRewards(Player player, List<CrateReward> rewards) {
        for (CrateReward reward : rewards) {
            giveReward(player, reward);
        }
    }
}
