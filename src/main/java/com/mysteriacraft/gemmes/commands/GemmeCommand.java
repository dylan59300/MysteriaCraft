package com.mysteriacraft.gemmes.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.gemmes.GemmeManager;
import com.mysteriacraft.gemmes.GemmeService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /gemme inserer : insere la gemme tenue en main secondaire dans l'equipement en main principale. */
public class GemmeCommand implements CommandExecutor {

    private final ConfigManager gemmesConfig;
    private final GemmeManager manager;
    private final GemmeService service;
    private final MessageManager messages;

    public GemmeCommand(ConfigManager gemmesConfig, GemmeManager manager, GemmeService service, MessageManager messages) {
        this.gemmesConfig = gemmesConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.gemme.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            gemmesConfig.reload();
            manager.load();
            messages.send(sender, "gemme.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("inserer")) {
            service.inserer(player);
            return true;
        }
        messages.send(player, "gemme.usage");
        return true;
    }
}
