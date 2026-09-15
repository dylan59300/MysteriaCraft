package com.mysteriacraft.enchantement.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.enchantement.EnchantementManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /enchantementadmin reload (admin) : recharge enchantement.yml (pool, materiau, catalyseur, cout).
 */
public class EnchantementAdminCommand implements CommandExecutor {

    private final ConfigManager enchantementConfig;
    private final EnchantementManager manager;
    private final MessageManager messages;

    public EnchantementAdminCommand(ConfigManager enchantementConfig, EnchantementManager manager, MessageManager messages) {
        this.enchantementConfig = enchantementConfig;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.enchantement.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        if (args.length < 1 || !args[0].equalsIgnoreCase("reload")) {
            messages.send(sender, "enchantement.usage");
            return true;
        }
        enchantementConfig.reload();
        manager.loadConfig();
        messages.send(sender, "enchantement.reload");
        return true;
    }
}
