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

public class EcoCommand implements CommandExecutor {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public EcoCommand(Plugin plugin, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.eco.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }

        if (args.length < 2) {
            messages.send(sender, "economie.eco-usage");
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];
        double amount = 0;

        boolean needsAmount = !action.equals("reset");
        if (needsAmount) {
            if (args.length < 3) {
                messages.send(sender, "economie.eco-usage");
                return true;
            }
            try {
                amount = Double.parseDouble(args[2].replace(',', '.'));
            } catch (NumberFormatException e) {
                messages.send(sender, "economie.eco-montant-invalide");
                return true;
            }
            if (amount < 0) {
                messages.send(sender, "economie.eco-montant-invalide");
                return true;
            }
        }

        final double finalAmount = amount;
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            apply(sender, action, onlineTarget.getUniqueId(), onlineTarget.getName(), finalAmount);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUuid = economyManager.findUuidByName(targetName);
            if (targetUuid == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "general.joueur-introuvable"));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> apply(sender, action, targetUuid, targetName, finalAmount));
        });
        return true;
    }

    private void apply(CommandSender sender, String action, UUID targetUuid, String targetName, double amount) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("joueur", targetName);
        placeholders.put("montant", economyManager.format(amount));

        switch (action) {
            case "give" -> {
                economyManager.deposit(targetUuid, amount);
                messages.send(sender, "economie.eco-give", placeholders);
            }
            case "take" -> {
                economyManager.withdraw(targetUuid, amount);
                messages.send(sender, "economie.eco-take", placeholders);
            }
            case "set" -> {
                economyManager.setBalance(targetUuid, amount);
                messages.send(sender, "economie.eco-set", placeholders);
            }
            case "reset" -> {
                double start = plugin.getConfig().getDouble("economie.solde-depart", 0.0);
                economyManager.setBalance(targetUuid, start);
                messages.send(sender, "economie.eco-reset", placeholders);
            }
            default -> messages.send(sender, "economie.eco-usage");
        }
    }
}
