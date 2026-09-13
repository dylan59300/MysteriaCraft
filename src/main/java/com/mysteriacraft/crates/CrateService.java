package com.mysteriacraft.crates;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.crates.gui.InstantCrateGui;
import com.mysteriacraft.crates.gui.QuickRevealCrateGui;
import com.mysteriacraft.crates.gui.RouletteCrateGui;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestre l'ouverture d'une caisse : permission, consommation d'une cle virtuelle (async),
 * tirage pondere de la recompense, puis lance l'animation GUI correspondante.
 */
public class CrateService {

    private final Plugin plugin;
    private final CrateManager crateManager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public CrateService(Plugin plugin, CrateManager crateManager, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.crateManager = crateManager;
        this.economyManager = economyManager;
        this.messages = messages;
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

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean consumed = crateManager.consumeKey(player.getUniqueId(), crate.id());
            if (!consumed) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("caisse", crate.displayName());
                    messages.send(player, "crates.pas-de-cle", placeholders);
                });
                return;
            }

            CrateReward reward = crateManager.pickReward(crate);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (reward == null) {
                    messages.send(player, "crates.aucune-recompense");
                    return;
                }
                launchAnimation(player, crate, reward);
            });
        });
    }

    private void launchAnimation(Player player, Crate crate, CrateReward reward) {
        switch (crate.animation()) {
            case ROULETTE -> new RouletteCrateGui(plugin, player, crate, reward, crateManager, this, messages).open();
            case QUICK_REVEAL -> new QuickRevealCrateGui(plugin, player, crate, reward, crateManager, this, messages).open();
            case INSTANT -> new InstantCrateGui(player, crate, reward, this, messages).open();
        }
    }

    /** Applique reellement la recompense (objet ou credit d'economie) et previent le joueur. Appele a la fin de l'animation. */
    public void giveReward(Player player, CrateReward reward) {
        if (reward.type() == RewardType.ECONOMIE) {
            economyManager.deposit(player.getUniqueId(), reward.economyAmount());
        } else if (reward.item() != null) {
            ItemStack toGive = reward.item().clone();
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(toGive);
            if (!leftovers.isEmpty()) {
                Location dropLocation = player.getLocation();
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItemNaturally(dropLocation, leftover);
                }
                messages.send(player, "kits.inventaire-plein");
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("recompense", reward.displayName());
        messages.send(player, "crates.gain", placeholders);
    }
}
