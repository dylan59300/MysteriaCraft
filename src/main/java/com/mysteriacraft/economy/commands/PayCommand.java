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

public class PayCommand implements CommandExecutor {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public PayCommand(Plugin plugin, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length < 2) {
            messages.send(sender, "economie.pay-usage");
            return true;
        }

        String targetName = args[0];
        double amount;
        try {
            amount = Double.parseDouble(args[1].replace(',', '.'));
        } catch (NumberFormatException e) {
            messages.send(sender, "economie.pay-montant-invalide");
            return true;
        }

        if (amount <= 0) {
            messages.send(sender, "economie.pay-montant-invalide");
            return true;
        }

        if (targetName.equalsIgnoreCase(payer.getName())) {
            messages.send(sender, "economie.pay-soi-meme");
            return true;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            executeTransfer(payer, onlineTarget.getUniqueId(), onlineTarget.getName(), amount);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUuid = economyManager.findUuidByName(targetName);
            if (targetUuid == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "general.joueur-introuvable"));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> executeTransfer(payer, targetUuid, targetName, amount));
        });
        return true;
    }

    private void executeTransfer(Player payer, UUID targetUuid, String targetName, double amount) {
        if (!economyManager.has(payer.getUniqueId(), amount)) {
            messages.send(payer, "economie.pay-fonds-insuffisants");
            return;
        }

        boolean success = economyManager.transfer(payer.getUniqueId(), targetUuid, amount);
        if (!success) {
            messages.send(payer, "economie.pay-fonds-insuffisants");
            return;
        }

        Map<String, String> senderPlaceholders = new HashMap<>();
        senderPlaceholders.put("joueur", targetName);
        senderPlaceholders.put("montant", economyManager.format(amount));
        messages.send(payer, "economie.pay-envoye", senderPlaceholders);

        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        if (onlineTarget != null) {
            Map<String, String> targetPlaceholders = new HashMap<>();
            targetPlaceholders.put("joueur", payer.getName());
            targetPlaceholders.put("montant", economyManager.format(amount));
            messages.send(onlineTarget, "economie.pay-recu", targetPlaceholders);
        }
    }
}
