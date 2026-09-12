package com.mysteriacraft.economy.commands;

import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayConfirmCommand implements CommandExecutor {

    private final PayCommand payCommand;
    private final MessageManager messages;

    public PayConfirmCommand(PayCommand payCommand, MessageManager messages) {
        this.payCommand = payCommand;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        payCommand.confirmPayment(player);
        return true;
    }
}
