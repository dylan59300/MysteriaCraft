package com.mysteriacraft.luckyblock.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /luckyblock buy <famille> [quantite]
 */
public class LuckyBlockCommand implements CommandExecutor {

    private final LuckyBlockService service;
    private final MessageManager messages;

    public LuckyBlockCommand(LuckyBlockService service, MessageManager messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("buy")) {
            messages.send(sender, "luckyblock.usage");
            return true;
        }

        String familyId = args[1];
        int quantity = 1;
        if (args.length >= 3) {
            try {
                quantity = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                messages.send(sender, "luckyblock.usage");
                return true;
            }
        }

        service.buy(player, familyId, quantity);
        return true;
    }
}
