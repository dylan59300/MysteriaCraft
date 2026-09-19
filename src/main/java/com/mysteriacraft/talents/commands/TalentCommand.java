package com.mysteriacraft.talents.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.talents.TalentManager;
import com.mysteriacraft.talents.TalentService;
import com.mysteriacraft.talents.gui.TalentGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** /talents : ouvre l'arbre de talents. /talents reload : recharge talents.yml (admin). */
public class TalentCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ConfigManager talentsConfig;
    private final TalentManager manager;
    private final TalentService service;
    private final MessageManager messages;

    public TalentCommand(Plugin plugin, ConfigManager talentsConfig, TalentManager manager, TalentService service, MessageManager messages) {
        this.plugin = plugin;
        this.talentsConfig = talentsConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.talents.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            talentsConfig.reload();
            manager.loadNodes();
            messages.send(sender, "talents.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int points = manager.getPoints(player.getUniqueId());
            List<String> unlocked = manager.getUnlockedNodeIds(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    new TalentGui(player, manager, service, messages, points, unlocked).open();
                }
            });
        });
        return true;
    }
}
