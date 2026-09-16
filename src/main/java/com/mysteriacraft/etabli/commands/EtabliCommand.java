package com.mysteriacraft.etabli.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.etabli.EtabliManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** /etabli reload : recharge etabli.yml (admin). */
public class EtabliCommand implements CommandExecutor {

    private final ConfigManager etabliConfig;
    private final EtabliManager manager;
    private final MessageManager messages;

    public EtabliCommand(ConfigManager etabliConfig, EtabliManager manager, MessageManager messages) {
        this.etabliConfig = etabliConfig;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.etabli.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        etabliConfig.reload();
        manager.load();
        messages.send(sender, "etabli.reload");
        return true;
    }
}
