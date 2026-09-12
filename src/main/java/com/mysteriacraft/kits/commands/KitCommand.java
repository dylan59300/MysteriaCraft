package com.mysteriacraft.kits.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.kits.KitGui;
import com.mysteriacraft.kits.KitManager;
import com.mysteriacraft.kits.KitService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class KitCommand implements CommandExecutor {

    private final KitManager kitManager;
    private final KitService kitService;
    private final MessageManager messages;

    public KitCommand(KitManager kitManager, KitService kitService, MessageManager messages) {
        this.kitManager = kitManager;
        this.kitService = kitService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length == 0) {
            new KitGui(player, kitManager, kitService, messages).open();
            return true;
        }

        kitService.claim(player, args[0]);
        return true;
    }
}
