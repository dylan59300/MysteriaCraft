package com.mysteriacraft.economy.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Recharge config.yml et messages.yml depuis le disque sans redemarrer le serveur.
 */
public class EcoReloadCommand implements CommandExecutor {

    private final ConfigManager config;
    private final ConfigManager messagesConfig;
    private final MessageManager messages;

    public EcoReloadCommand(ConfigManager config, ConfigManager messagesConfig, MessageManager messages) {
        this.config = config;
        this.messagesConfig = messagesConfig;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.eco.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }

        config.reload();
        messagesConfig.reload();
        messages.send(sender, "economie.eco-reload");
        return true;
    }
}
