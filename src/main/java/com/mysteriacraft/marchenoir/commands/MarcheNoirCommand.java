package com.mysteriacraft.marchenoir.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.marchenoir.MarcheNoirManager;
import com.mysteriacraft.marchenoir.MarcheNoirService;
import com.mysteriacraft.marchenoir.gui.MarcheNoirGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /marchenoir : ouvre le menu du Marche Noir. /marchenoir reload : recharge la config (admin). */
public class MarcheNoirCommand implements CommandExecutor {

    private final ConfigManager marcheNoirConfig;
    private final MarcheNoirManager manager;
    private final MarcheNoirService service;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public MarcheNoirCommand(ConfigManager marcheNoirConfig, MarcheNoirManager manager, MarcheNoirService service,
                              EconomyManager economyManager, MessageManager messages) {
        this.marcheNoirConfig = marcheNoirConfig;
        this.manager = manager;
        this.service = service;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.marchenoir.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            marcheNoirConfig.reload();
            manager.load();
            messages.send(sender, "marchenoir.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        new MarcheNoirGui(player, manager, service, economyManager, messages).open();
        return true;
    }
}
