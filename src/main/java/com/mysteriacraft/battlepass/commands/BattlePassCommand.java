package com.mysteriacraft.battlepass.commands;

import com.mysteriacraft.battlepass.BattlePassManager;
import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.battlepass.gui.BattlePassGui;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public class BattlePassCommand implements CommandExecutor {

    private final Plugin plugin;
    private final BattlePassManager manager;
    private final BattlePassService service;
    private final MessageManager messages;

    public BattlePassCommand(Plugin plugin, BattlePassManager manager, BattlePassService service, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
                case "prestige" -> {
                    service.prestige(player);
                    return true;
                }
                case "sprint" -> {
                    service.sprint(player);
                    return true;
                }
                case "stats" -> {
                    handleStats(player);
                    return true;
                }
                case "donner" -> {
                    handleGift(player, args);
                    return true;
                }
                case "titre" -> {
                    service.setActiveTitle(player, args.length >= 2 ? args[1] : null);
                    return true;
                }
                case "titres" -> {
                    handleListTitles(player);
                    return true;
                }
                default -> {
                }
            }
        }

        openGui(player);
        return true;
    }

    private void openGui(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long xp = manager.getXp(player.getUniqueId());
            boolean premium = service.isPremiumEffective(player);
            var claims = manager.getAllClaims(player.getUniqueId());
            int prestige = manager.getPrestige(player.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () ->
                    new BattlePassGui(plugin, player, manager, service, messages, xp, premium, claims, prestige).open());
        });
    }

    private void handleStats(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var history = manager.getRecentXpHistory(player.getUniqueId(), 7);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (history.isEmpty()) {
                    messages.send(player, "battlepass.stats-vide");
                    return;
                }
                messages.send(player, "battlepass.stats-titre");
                for (BattlePassManager.XpHistoryEntry entry : history) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("jour", entry.jour());
                    placeholders.put("xp", String.valueOf(entry.xpGagne()));
                    messages.send(player, "battlepass.stats-ligne", placeholders);
                }
            });
        });
    }

    private void handleGift(Player player, String[] args) {
        if (args.length < 3) {
            messages.send(player, "battlepass.don-usage");
            return;
        }
        Player receiver = Bukkit.getPlayerExact(args[1]);
        if (receiver == null) {
            messages.send(player, "general.joueur-introuvable");
            return;
        }
        int levelsToGift;
        try {
            levelsToGift = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            messages.send(player, "battlepass.don-usage");
            return;
        }
        if (levelsToGift <= 0) {
            messages.send(player, "battlepass.don-usage");
            return;
        }
        service.giftLevels(player, receiver, levelsToGift);
    }

    private void handleListTitles(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var titres = manager.getUnlockedTitles(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (titres.isEmpty()) {
                    messages.send(player, "battlepass.titres-vide");
                    return;
                }
                messages.send(player, "battlepass.titres-titre");
                for (String titre : titres) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("titre", titre);
                    messages.send(player, "battlepass.titres-ligne", placeholders);
                }
            });
        });
    }
}
