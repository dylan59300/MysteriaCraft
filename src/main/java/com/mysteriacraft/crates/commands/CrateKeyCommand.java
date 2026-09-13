package com.mysteriacraft.crates.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.crates.Crate;
import com.mysteriacraft.crates.CrateManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Commande admin de gestion des cles virtuelles : /cratekey <give|take|set> <joueur> <caisse> <quantite>
 */
public class CrateKeyCommand implements CommandExecutor {

    private final Plugin plugin;
    private final CrateManager crateManager;
    private final MessageManager messages;

    public CrateKeyCommand(Plugin plugin, CrateManager crateManager, MessageManager messages) {
        this.plugin = plugin;
        this.crateManager = crateManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.crate.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        if (args.length < 4) {
            messages.send(sender, "crates.key-usage");
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];
        String crateId = args[2];

        Crate crate = crateManager.getCrate(crateId);
        if (crate == null) {
            messages.send(sender, "crates.introuvable");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            messages.send(sender, "crates.key-montant-invalide");
            return true;
        }
        if (amount < 0) {
            messages.send(sender, "crates.key-montant-invalide");
            return true;
        }

        if (!action.equals("give") && !action.equals("take") && !action.equals("set")) {
            messages.send(sender, "crates.key-usage");
            return true;
        }

        // Bukkit.getOfflinePlayer(String) peut declencher un appel reseau bloquant si le joueur
        // n'est pas deja connu du serveur : execution hors du thread principal par precaution.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            int newCount = switch (action) {
                case "give" -> crateManager.addKeys(target.getUniqueId(), crate.id(), amount);
                case "take" -> crateManager.addKeys(target.getUniqueId(), crate.id(), -amount);
                default -> crateManager.setKeys(target.getUniqueId(), crate.id(), amount);
            };

            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("joueur", target.getName() != null ? target.getName() : targetName);
                placeholders.put("caisse", crate.displayName());
                placeholders.put("total", String.valueOf(newCount));
                messages.send(sender, "crates.key-modifie", placeholders);
            });
        });
        return true;
    }
}
