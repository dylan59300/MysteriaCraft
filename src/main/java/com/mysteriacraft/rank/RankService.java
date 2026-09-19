package com.mysteriacraft.rank;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Traite /prestige : verifie le palier suivant (lecture SQLite, hors thread principal) puis
 * debite le cout et fait progresser le joueur d'un palier. */
public class RankService {

    private final Plugin plugin;
    private final RankManager manager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public RankService(Plugin plugin, RankManager manager, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public void showInfo(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int niveau = manager.getNiveau(uuid);
            RankManager.PrestigeTier next = manager.getNextTier(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("niveau", String.valueOf(niveau));
                placeholders.put("bonus", formatPercent(manager.getBonusVentePourcent(uuid)));
                if (next == null) {
                    messages.send(player, "prestige.info-max", placeholders);
                } else {
                    placeholders.put("cout", economyManager.format(next.cout()));
                    placeholders.put("nom-suivant", next.nom());
                    placeholders.put("bonus-suivant", formatPercent(next.bonusVentePourcent()));
                    messages.send(player, "prestige.info", placeholders);
                }
            });
        });
    }

    public void prestige(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RankManager.PrestigeTier next = manager.getNextTier(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (next == null) {
                    messages.send(player, "prestige.max-atteint");
                    return;
                }
                if (!economyManager.withdraw(uuid, next.cout())) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("cout", economyManager.format(next.cout()));
                    messages.send(player, "prestige.fonds-insuffisants", placeholders);
                    return;
                }
                manager.setNiveau(uuid, next.niveau());

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("niveau", String.valueOf(next.niveau()));
                placeholders.put("nom", next.nom());
                placeholders.put("bonus", formatPercent(next.bonusVentePourcent()));
                messages.send(player, "prestige.reussi", placeholders);
            });
        });
    }

    private String formatPercent(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }
}
