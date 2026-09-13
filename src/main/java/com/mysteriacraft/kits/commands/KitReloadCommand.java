package com.mysteriacraft.kits.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.kits.KitManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class KitReloadCommand implements CommandExecutor {

    private final ConfigManager kitsConfig;
    private final KitManager kitManager;
    private final MessageManager messages;

    public KitReloadCommand(ConfigManager kitsConfig, KitManager kitManager, MessageManager messages) {
        this.kitsConfig = kitsConfig;
        this.kitManager = kitManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.kit.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        kitsConfig.reload();
        kitManager.loadKits();
        messages.send(sender, "kits.reload");
        return true;
    }
}
