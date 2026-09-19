package com.mysteriacraft.runes.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.runes.RuneManager;
import com.mysteriacraft.runes.RuneService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /rune graver : grave la rune tenue en main secondaire sur l'arme en main principale. */
public class RuneCommand implements CommandExecutor {

    private final ConfigManager runesConfig;
    private final RuneManager manager;
    private final RuneService service;
    private final MessageManager messages;

    public RuneCommand(ConfigManager runesConfig, RuneManager manager, RuneService service, MessageManager messages) {
        this.runesConfig = runesConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.rune.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            runesConfig.reload();
            manager.load();
            messages.send(sender, "rune.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("graver")) {
            service.graver(player);
            return true;
        }
        messages.send(player, "rune.usage");
        return true;
    }
}
