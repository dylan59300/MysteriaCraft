package com.mysteriacraft.quests.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.quests.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Commande admin des quetes : /questsadmin reload | reset <joueur>
 */
public class QuestsAdminCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ConfigManager questsConfig;
    private final QuestManager questManager;
    private final MessageManager messages;

    public QuestsAdminCommand(Plugin plugin, ConfigManager questsConfig, QuestManager questManager, MessageManager messages) {
        this.plugin = plugin;
        this.questsConfig = questsConfig;
        this.questManager = questManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.quests.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("reset")) {
            String targetName = args[1];
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                questManager.resetAllProgress(target.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("joueur", target.getName() != null ? target.getName() : targetName);
                    messages.send(sender, "quests.reset-effectue", placeholders);
                });
            });
            return true;
        }

        questsConfig.reload();
        questManager.loadQuests();
        messages.send(sender, "quests.reload");
        return true;
    }
}
