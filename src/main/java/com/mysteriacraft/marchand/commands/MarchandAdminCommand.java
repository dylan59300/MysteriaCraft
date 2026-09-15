package com.mysteriacraft.marchand.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.marchand.MarchandManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /pnjmarchand reload (admin) : recharge marchand.yml (offres, nom du PNJ, id de la monnaie).
 */
public class MarchandAdminCommand implements CommandExecutor {

    private final ConfigManager marchandConfig;
    private final MarchandManager manager;
    private final MessageManager messages;

    public MarchandAdminCommand(ConfigManager marchandConfig, MarchandManager manager, MessageManager messages) {
        this.marchandConfig = marchandConfig;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.pnjmarchand.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        if (args.length < 1 || !args[0].equalsIgnoreCase("reload")) {
            messages.send(sender, "marchand.usage");
            return true;
        }
        marchandConfig.reload();
        manager.loadConfig();
        messages.send(sender, "marchand.reload");
        return true;
    }
}
