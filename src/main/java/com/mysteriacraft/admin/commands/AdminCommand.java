package com.mysteriacraft.admin.commands;

import com.mysteriacraft.admin.gui.AdminPanelGui;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /admin : ouvre le panel admin unifie (voir AdminPanelGui/AdminRegistry), qui centralise le
 * reload (et l'editeur en jeu, quand il existe) de tous les modules du plugin.
 */
public class AdminCommand implements CommandExecutor {

    private final MessageManager messages;

    public AdminCommand(MessageManager messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        new AdminPanelGui(player, messages).open();
        return true;
    }
}
