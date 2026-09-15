package com.mysteriacraft.battlepass.commands;

import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BattlePassConfirmPremiumCommand implements CommandExecutor {

    private final BattlePassService service;
    private final MessageManager messages;

    public BattlePassConfirmPremiumCommand(BattlePassService service, MessageManager messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        service.confirmPremiumPurchase(player);
        return true;
    }
}
