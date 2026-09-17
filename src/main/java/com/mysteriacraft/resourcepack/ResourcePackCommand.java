package com.mysteriacraft.resourcepack;

import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /resourcepack : renvoie le resource pack au joueur. /resourcepack reload (admin) : recharge resourcepack.yml. */
public class ResourcePackCommand implements CommandExecutor {

    private final ResourcePackManager manager;
    private final ResourcePackListener listener;
    private final MessageManager messages;

    public ResourcePackCommand(ResourcePackManager manager, ResourcePackListener listener, MessageManager messages) {
        this.manager = manager;
        this.listener = listener;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.resourcepack.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            manager.reload();
            messages.send(sender, "resourcepack.reload");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        if (!manager.isConfigure()) {
            messages.send(player, "resourcepack.non-configure");
            return true;
        }
        listener.envoyer(player);
        messages.send(player, "resourcepack.envoye");
        return true;
    }
}
