package com.mysteriacraft.quests.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.quests.QuestDefinition;
import com.mysteriacraft.quests.QuestManager;
import com.mysteriacraft.quests.QuestService;
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
    private final QuestService questService;
    private final ConfigManager questsConfig;
    private final MessageManager messages;

    public QuestsCommand(Plugin plugin, QuestManager questManager, QuestService questService,
                          ConfigManager questsConfig, MessageManager messages) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.questService = questService;
        this.questsConfig = questsConfig;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
                case "echanger" -> {
                    handleExchange(player, args);
                    return true;
                }
                case "boost" -> {
                    handleBoost(player, args);
                    return true;
                }
                case "accepter" -> {
                    handleAcceptContract(player, args);
                    return true;
                }
                case "historique" -> {
                    handleHistory(player);
                    return true;
                }
                default -> {
                }
            }
        }

        openGui(player);
        return true;
    }

    private void openGui(Player player) {
        // Synchrone comme le reste des acces SQLite du plugin (voir QuestService#registerProgress) :
        // evite tout acces concurrent a la Connection partagee, une requete locale etant instantanee.
        Map<String, QuestManager.ProgressSnapshot> progress = new HashMap<>();
        for (QuestDefinition quest : questManager.getActiveQuestsForPlayer(player.getUniqueId())) {
            progress.put(quest.id(), questManager.getProgress(player.getUniqueId(), quest));
        }
        new QuestsGui(player, questManager, messages, progress).open();
    }

    private QuestDefinition findActiveQuestForPlayer(Player player, String id) {
        for (QuestDefinition quest : questManager.getActiveQuestsForPlayer(player.getUniqueId())) {
            if (quest.id().equalsIgnoreCase(id)) {
                return quest;
            }
        }
        return null;
    }

    private void handleExchange(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "quests.usage-echanger");
            return;
        }
        int maxPerDay = Math.max(0, questsConfig.get().getInt("echanges-quotidiens-max", 1));
        if (questManager.countExchangesToday(player.getUniqueId()) >= maxPerDay) {
            messages.send(player, "quests.echanges-epuises");
            return;
        }
        QuestDefinition original = findActiveQuestForPlayer(player, args[1]);
        if (original == null) {
            messages.send(player, "quests.quete-introuvable");
            return;
        }
        if (questManager.getProgress(player.getUniqueId(), original).progression() > 0) {
            messages.send(player, "quests.echange-deja-commencee");
            return;
        }
        QuestDefinition replacement = questManager.exchangeDailyQuest(player.getUniqueId(), original.id());
        if (replacement == null) {
            messages.send(player, "quests.echange-impossible");
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("ancienne", original.displayName());
        placeholders.put("nouvelle", replacement.displayName());
        messages.send(player, "quests.echange-reussi", placeholders);
    }

    private void handleBoost(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "quests.usage-boost");
            return;
        }
        QuestDefinition quest = findActiveQuestForPlayer(player, args[1]);
        if (quest == null) {
            messages.send(player, "quests.quete-introuvable");
            return;
        }
        double price = questsConfig.get().getDouble("boost-prix", 500);
        questService.buyBoost(player, quest, price);
    }

    private void handleAcceptContract(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "quests.usage-accepter");
            return;
        }
        QuestDefinition quest = findActiveQuestForPlayer(player, args[1]);
        if (quest == null || !quest.contrat()) {
            messages.send(player, "quests.quete-introuvable");
            return;
        }
        if (questManager.isContractAccepted(player.getUniqueId(), quest)) {
            messages.send(player, "quests.contrat-deja-accepte");
            return;
        }
        questManager.acceptContract(player.getUniqueId(), quest);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quete", quest.displayName());
        messages.send(player, "quests.contrat-accepte", placeholders);
    }

    private void handleHistory(Player player) {
        var entries = questManager.getRecentHistory(player.getUniqueId(), 10);
        if (entries.isEmpty()) {
            messages.send(player, "quests.historique-vide");
            return;
        }
        messages.send(player, "quests.historique-titre");
        for (QuestManager.HistoryEntry entry : entries) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("quete", entry.questNom());
            placeholders.put("xp", String.valueOf(entry.xp()));
            placeholders.put("date", entry.dateIso());
            messages.send(player, "quests.historique-ligne", placeholders);
        }
    }
}
