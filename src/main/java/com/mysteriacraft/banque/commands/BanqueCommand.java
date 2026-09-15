package com.mysteriacraft.banque.commands;

import com.mysteriacraft.banque.BanqueManager;
import com.mysteriacraft.banque.BanqueService;
import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /banque [solde|deposer <montant>|retirer <montant>|reload (admin)]
 */
public class BanqueCommand implements CommandExecutor {

    private final ConfigManager banqueConfig;
    private final BanqueManager manager;
    private final BanqueService service;
    private final MessageManager messages;

    public BanqueCommand(ConfigManager banqueConfig, BanqueManager manager, BanqueService service, MessageManager messages) {
        this.banqueConfig = banqueConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.banque.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            banqueConfig.reload();
            manager.loadConfig();
            messages.send(sender, "banque.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("solde")) {
            service.consulterSolde(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("deposer") || args[0].equalsIgnoreCase("retirer")) {
            if (args.length < 2) {
                messages.send(player, "banque.usage");
                return true;
            }
            double montant;
            try {
                montant = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                messages.send(player, "banque.usage");
                return true;
            }
            if (montant <= 0) {
                messages.send(player, "banque.usage");
                return true;
            }
            if (args[0].equalsIgnoreCase("deposer")) {
                service.deposer(player, montant);
            } else {
                service.retirer(player, montant);
            }
            return true;
        }

        messages.send(player, "banque.usage");
        return true;
    }
}
