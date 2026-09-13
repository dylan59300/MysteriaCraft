package com.mysteriacraft.crates.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.crates.CrateManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CrateReloadCommand implements CommandExecutor {

    private final ConfigManager cratesConfig;
    private final CrateManager crateManager;
    private final MessageManager messages;

    public CrateReloadCommand(ConfigManager cratesConfig, CrateManager crateManager, MessageManager messages) {
        this.cratesConfig = cratesConfig;
        this.crateManager = crateManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.crate.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        cratesConfig.reload();
        crateManager.loadCrates();
        messages.send(sender, "crates.reload");
        return true;
    }
}
