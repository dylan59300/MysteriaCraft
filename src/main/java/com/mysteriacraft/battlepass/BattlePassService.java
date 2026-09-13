package com.mysteriacraft.battlepass;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestre la progression du BattlePass : gain d'xp (avec detection de passage de niveau),
 * achat de la piste premium, et reclamation des recompenses (gratuite/premium) par palier.
 */
public class BattlePassService {

    public static final String TRACK_FREE = "GRATUIT";
    public static final String TRACK_PREMIUM = "PREMIUM";

    private final Plugin plugin;
    private final BattlePassManager manager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public BattlePassService(Plugin plugin, BattlePassManager manager, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    /** Ajoute de l'xp a un joueur en ligne et le previent s'il passe un ou plusieurs niveaux. */
    public void addXp(Player player, long amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long oldXp = manager.getXp(player.getUniqueId());
            int oldLevel = manager.computeLevel(oldXp);
            long newXp = manager.addXp(player.getUniqueId(), amount);
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

    public void buyPremium(Player player) {
        double price = manager.getPremiumPrice();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean alreadyPremium = manager.isPremium(player.getUniqueId());
            if (alreadyPremium) {
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
            if (TRACK_PREMIUM.equals(track) && !manager.isPremium(player.getUniqueId())) {
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
        messages.send(player, "battlepass.recompense-recue", placeholders);
    }
}
