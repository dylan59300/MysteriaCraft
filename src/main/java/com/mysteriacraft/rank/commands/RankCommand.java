package com.mysteriacraft.rank.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.rank.RankService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /prestige : affiche le palier actuel et le suivant. /prestige confirmer : effectue le prestige. */
public class RankCommand implements CommandExecutor {

    private final RankService service;
    private final MessageManager messages;

    public RankCommand(RankService service, MessageManager messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("confirmer")) {
            service.prestige(player);
            return true;
        }

        service.showInfo(player);
        return true;
    }
}
