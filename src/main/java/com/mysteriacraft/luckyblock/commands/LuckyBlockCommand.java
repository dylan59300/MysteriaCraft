package com.mysteriacraft.luckyblock.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import com.mysteriacraft.luckyblock.gui.LuckyBlockGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /luckyblock (sans argument) ouvre le menu ; /luckyblock buy <famille> [quantite] achete direct.
 */
public class LuckyBlockCommand implements CommandExecutor {

    private final LuckyBlockManager manager;
    private final LuckyBlockService service;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public LuckyBlockCommand(LuckyBlockManager manager, LuckyBlockService service,
                              EconomyManager economyManager, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length == 0) {
            new LuckyBlockGui(player, manager, service, economyManager, messages).open();
            return true;
        }

        if (!args[0].equalsIgnoreCase("buy") || args.length < 2) {
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
