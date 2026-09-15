package com.mysteriacraft.classes.commands;

import com.mysteriacraft.classes.ClasseManager;
import com.mysteriacraft.classes.ClasseService;
import com.mysteriacraft.classes.gui.ClasseGui;
import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * /classe [choisir <id>|reload (admin)] : sans argument, ouvre le menu de choix.
 */
public class ClasseCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ConfigManager classesConfig;
    private final ClasseManager manager;
    private final ClasseService service;
    private final MessageManager messages;

    public ClasseCommand(Plugin plugin, ConfigManager classesConfig, ClasseManager manager,
                          ClasseService service, MessageManager messages) {
        this.plugin = plugin;
        this.classesConfig = classesConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.classes.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            classesConfig.reload();
            manager.loadConfig();
            messages.send(sender, "classes.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("choisir")) {
            service.choisir(player, args[1]);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String classeActive = manager.getClasseActive(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () ->
                    new ClasseGui(player, manager, service, messages, classeActive).open());
        });
        return true;
    }
}
