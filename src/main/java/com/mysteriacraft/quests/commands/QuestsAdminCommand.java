package com.mysteriacraft.quests.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.quests.QuestManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class QuestsAdminCommand implements CommandExecutor {

    private final ConfigManager questsConfig;
    private final QuestManager questManager;
    private final MessageManager messages;

    public QuestsAdminCommand(ConfigManager questsConfig, QuestManager questManager, MessageManager messages) {
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
        questsConfig.reload();
        questManager.loadQuests();
        messages.send(sender, "quests.reload");
        return true;
    }
}
