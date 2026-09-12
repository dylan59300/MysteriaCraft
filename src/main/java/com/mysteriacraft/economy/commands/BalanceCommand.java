package com.mysteriacraft.economy.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BalanceCommand implements CommandExecutor {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public BalanceCommand(Plugin plugin, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.commande-joueur-uniquement");
                return true;
            }
            double balance = economyManager.getBalance(player.getUniqueId());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("montant", economyManager.format(balance));
            messages.send(sender, "economie.solde-personnel", placeholders);
            return true;
        }

        String targetName = args[0];
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            double balance = economyManager.getBalance(online.getUniqueId());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("joueur", online.getName());
            placeholders.put("montant", economyManager.format(balance));
            messages.send(sender, "economie.solde-autre", placeholders);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = economyManager.findUuidByName(targetName);
            if (uuid == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "general.joueur-introuvable"));
                return;
            }
            double balance = economyManager.getBalance(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("joueur", targetName);
                placeholders.put("montant", economyManager.format(balance));
                messages.send(sender, "economie.solde-autre", placeholders);
            });
        });
        return true;
    }
}
