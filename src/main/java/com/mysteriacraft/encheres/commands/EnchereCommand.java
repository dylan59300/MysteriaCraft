package com.mysteriacraft.encheres.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.encheres.EnchereManager;
import com.mysteriacraft.encheres.EnchereService;
import com.mysteriacraft.encheres.gui.EnchereGui;
import com.mysteriacraft.encheres.gui.EnchereMesVentesGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * /hoteldesventes [vendre <prix>|mesventes|annuler <id>|reload (admin)] : sans argument, ouvre
 * le catalogue de toutes les annonces actives.
 */
public class EnchereCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ConfigManager encheresConfig;
    private final EnchereManager manager;
    private final EnchereService service;
    private final MessageManager messages;

    public EnchereCommand(Plugin plugin, ConfigManager encheresConfig, EnchereManager manager,
                           EnchereService service, MessageManager messages) {
        this.plugin = plugin;
        this.encheresConfig = encheresConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.encheres.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            encheresConfig.reload();
            manager.loadConfig();
            messages.send(sender, "encheres.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("vendre")) {
            double prix;
            try {
                prix = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                messages.send(player, "encheres.usage");
                return true;
            }
            if (prix <= 0) {
                messages.send(player, "encheres.usage");
                return true;
            }
            service.vendre(player, prix);
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("mesventes")) {
            new EnchereMesVentesGui(plugin, player, manager, service, messages).open();
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("annuler")) {
            try {
                service.annuler(player, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                messages.send(player, "encheres.usage");
            }
            return true;
        }

        new EnchereGui(plugin, player, manager, service, messages).open();
        return true;
    }
}
