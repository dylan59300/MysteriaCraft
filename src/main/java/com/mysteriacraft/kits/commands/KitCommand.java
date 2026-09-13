package com.mysteriacraft.kits.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.kits.KitGui;
import com.mysteriacraft.kits.KitManager;
import com.mysteriacraft.kits.KitService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class KitCommand implements CommandExecutor {

    private final Plugin plugin;
    private final KitManager kitManager;
    private final KitService kitService;
    private final MessageManager messages;

    public KitCommand(Plugin plugin, KitManager kitManager, KitService kitService, MessageManager messages) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.kitService = kitService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /kit reset <joueur> <kit> : sous-commande admin (prioritaire sur un eventuel kit nomme "reset")
        if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
            return handleReset(sender, args);
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length == 0) {
            new KitGui(player, kitManager, kitService, messages).open();
            return true;
        }

        kitService.claim(player, args[0]);
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mysteriacraft.kit.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "kits.reset-usage");
            return true;
        }
        if (!(sender instanceof Player admin)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        String targetName = args[1];
        String kitId = args[2];

        // Bukkit.getOfflinePlayer(String) peut declencher un appel reseau bloquant si le joueur
        // n'est pas deja connu du serveur : on l'execute hors du thread principal par precaution.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            Bukkit.getScheduler().runTask(plugin, () -> kitService.resetCooldown(admin, target, kitId));
        });
        return true;
    }
}
