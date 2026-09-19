package com.mysteriacraft.admin.commands;

import com.mysteriacraft.admin.AdminChatService;
import com.mysteriacraft.admin.AdminMaintenanceManager;
import com.mysteriacraft.admin.AdminMuteManager;
import com.mysteriacraft.admin.gui.AdminPanelGui;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /admin : ouvre le panel admin unifie (voir AdminPanelGui/AdminRegistry), qui centralise le
 * reload (et l'editeur en jeu, quand il existe) de tous les modules du plugin, plus la maintenance
 * par module, le broadcast rapide et les sanctions rapides.
 */
public class AdminCommand implements CommandExecutor {

    private final AdminMaintenanceManager maintenanceManager;
    private final AdminChatService chatService;
    private final AdminMuteManager muteManager;
    private final MessageManager messages;

    public AdminCommand(AdminMaintenanceManager maintenanceManager, AdminChatService chatService,
                         AdminMuteManager muteManager, MessageManager messages) {
        this.maintenanceManager = maintenanceManager;
        this.chatService = chatService;
        this.muteManager = muteManager;
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
        new AdminPanelGui(player, maintenanceManager, chatService, muteManager, messages).open();
        return true;
    }
}
