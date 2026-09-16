package com.mysteriacraft.metiers.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.metiers.MetierManager;
import com.mysteriacraft.metiers.MetierService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** /metier : affiche votre metier et niveau actuels. /metier choisir <id> : choisit un metier. */
public class MetierCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager metiersConfig;
    private final MetierManager manager;
    private final MetierService service;
    private final MessageManager messages;

    public MetierCommand(ConfigManager metiersConfig, MetierManager manager, MetierService service, MessageManager messages) {
        this.metiersConfig = metiersConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.metier.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            metiersConfig.reload();
            manager.loadMetiers();
            messages.send(sender, "metier.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("choisir")) {
            service.choisir(player, args[1]);
            return true;
        }
        service.showInfo(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("choisir"));
            if (sender.hasPermission("mysteriacraft.metier.admin")) {
                options.add("reload");
            }
            return options.stream().filter(o -> o.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("choisir")) {
            return manager.getMetiers().keySet().stream()
                    .filter(id -> id.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        return List.of();
    }
}
