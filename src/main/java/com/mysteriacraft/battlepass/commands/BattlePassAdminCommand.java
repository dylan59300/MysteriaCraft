package com.mysteriacraft.battlepass.commands;

import com.mysteriacraft.battlepass.BattlePassManager;
import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Commande admin du BattlePass : /battlepassadmin addxp <joueur> <montant> | reload
 * Tant que les Quetes ne sont pas branchees, c'est le principal moyen (avec l'xp passive)
 * de faire progresser les joueurs sur le BattlePass.
 */
public class BattlePassAdminCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ConfigManager battlepassConfig;
    private final BattlePassManager manager;
    private final BattlePassService service;
    private final MessageManager messages;

    public BattlePassAdminCommand(Plugin plugin, ConfigManager battlepassConfig, BattlePassManager manager,
                                   BattlePassService service, MessageManager messages) {
        this.plugin = plugin;
        this.battlepassConfig = battlepassConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.battlepass.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "battlepass.addxp-usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            battlepassConfig.reload();
            manager.loadLevels();
            messages.send(sender, "battlepass.reload");
            if (manager.checkAndArchiveSeasonIfNeeded()) {
                messages.send(sender, "battlepass.saison-archivee");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("nouvellesaison")) {
            manager.forceArchiveSeasonNow();
            messages.send(sender, "battlepass.saison-archivee");
            return true;
        }

        if (args[0].equalsIgnoreCase("addxp")) {
            if (args.length < 3) {
                messages.send(sender, "battlepass.addxp-usage");
                return true;
            }
            String targetName = args[1];
            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                messages.send(sender, "battlepass.addxp-usage");
                return true;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                messages.send(sender, "general.joueur-introuvable");
                return true;
            }

            service.addXp(target, amount);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("joueur", target.getName());
            placeholders.put("montant", String.valueOf(amount));
            messages.send(sender, "battlepass.addxp-donne", placeholders);
            return true;
        }

        messages.send(sender, "battlepass.addxp-usage");
        return true;
    }
}
