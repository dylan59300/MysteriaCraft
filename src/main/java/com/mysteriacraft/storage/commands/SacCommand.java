package com.mysteriacraft.storage.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.storage.StorageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /sac : ouvre le Sac personnel. /sac ameliorer : consomme l'item d'amelioration en main. */
public class SacCommand implements CommandExecutor {

    private final StorageService service;
    private final MessageManager messages;

    public SacCommand(StorageService service, MessageManager messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("ameliorer")) {
            service.upgradeSac(player);
            return true;
        }
        service.open(player, StorageService.TYPE_SAC);
        return true;
    }
}
