package com.mysteriacraft.guide.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.guide.GuideGui;
import com.mysteriacraft.guide.GuideManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /guide (ouvert a tous) | reload (admin uniquement).
 */
public class GuideCommand implements CommandExecutor {

    private final ConfigManager guideConfig;
    private final GuideManager manager;
    private final MessageManager messages;

    public GuideCommand(ConfigManager guideConfig, GuideManager manager, MessageManager messages) {
        this.guideConfig = guideConfig;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.guide.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            guideConfig.reload();
            manager.loadConfig();
            messages.send(sender, "guide.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        new GuideGui(player, manager, messages).open();
        return true;
    }
}
