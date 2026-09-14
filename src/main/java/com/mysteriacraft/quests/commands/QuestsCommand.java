package com.mysteriacraft.quests.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.quests.QuestDefinition;
import com.mysteriacraft.quests.QuestManager;
import com.mysteriacraft.quests.gui.QuestsGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public class QuestsCommand implements CommandExecutor {

    private final Plugin plugin;
    private final QuestManager questManager;
    private final MessageManager messages;

    public QuestsCommand(Plugin plugin, QuestManager questManager, MessageManager messages) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        // Synchrone comme le reste des acces SQLite du plugin (voir QuestService#registerProgress) :
        // evite tout acces concurrent a la Connection partagee, une requete locale etant instantanee.
        Map<String, QuestManager.ProgressSnapshot> progress = new HashMap<>();
        for (QuestDefinition quest : questManager.getAllQuests()) {
            progress.put(quest.id(), questManager.getProgress(player.getUniqueId(), quest));
        }
        new QuestsGui(player, questManager, messages, progress).open();
        return true;
    }
}
