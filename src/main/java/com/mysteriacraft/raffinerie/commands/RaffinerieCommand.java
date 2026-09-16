package com.mysteriacraft.raffinerie.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.raffinerie.RaffinerieManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** /raffinerie reload : recharge raffinerie.yml (admin). */
public class RaffinerieCommand implements CommandExecutor {

    private final ConfigManager raffinerieConfig;
    private final RaffinerieManager manager;
    private final MessageManager messages;

    public RaffinerieCommand(ConfigManager raffinerieConfig, RaffinerieManager manager, MessageManager messages) {
        this.raffinerieConfig = raffinerieConfig;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.raffinerie.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        raffinerieConfig.reload();
        manager.load();
        messages.send(sender, "raffinerie.reload");
        return true;
    }
}
