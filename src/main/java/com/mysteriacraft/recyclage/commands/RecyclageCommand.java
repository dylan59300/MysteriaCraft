package com.mysteriacraft.recyclage.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.recyclage.RecyclageManager;
import com.mysteriacraft.recyclage.RecyclageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /recycler : consomme l'item custom en main pour recuperer des materiaux. /recycler reload : admin. */
public class RecyclageCommand implements CommandExecutor {

    private final ConfigManager recyclageConfig;
    private final RecyclageManager manager;
    private final RecyclageService service;
    private final MessageManager messages;

    public RecyclageCommand(ConfigManager recyclageConfig, RecyclageManager manager, RecyclageService service, MessageManager messages) {
        this.recyclageConfig = recyclageConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.recyclage.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            recyclageConfig.reload();
            manager.load();
            messages.send(sender, "recyclage.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        service.recycler(player);
        return true;
    }
}
