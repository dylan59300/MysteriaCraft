package com.mysteriacraft.storage.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.storage.StorageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /coffrefort : ouvre le Coffre-fort personnel. /coffrefort ameliorer : paye pour l'agrandir. */
public class CoffreFortCommand implements CommandExecutor {

    private final StorageService service;
    private final MessageManager messages;

    public CoffreFortCommand(StorageService service, MessageManager messages) {
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
            service.upgradeCoffreFort(player);
            return true;
        }
        service.open(player, StorageService.TYPE_COFFREFORT);
        return true;
    }
}
