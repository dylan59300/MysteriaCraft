package com.mysteriacraft.quests;

import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Recoit les evenements de jeu (bloc casse, mob tue, peche...), fait progresser les quetes
 * correspondantes, et donne automatiquement l'xp de BattlePass + la recompense directe des
 * qu'une quete est terminee (pas de reclamation manuelle).
 */
public class QuestService {

    private final Plugin plugin;
    private final QuestManager questManager;
    private final BattlePassService battlePassService;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public QuestService(Plugin plugin, QuestManager questManager, BattlePassService battlePassService,
                         EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.battlePassService = battlePassService;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    /** A appeler depuis un listener Bukkit (thread principal) a chaque action pouvant faire progresser une quete. */
    public void registerProgress(Player player, QuestType type, String target, int amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (QuestDefinition quest : questManager.getAllQuests()) {
                if (quest.type() != type) {
                    continue;
                }
                if (quest.target() != null && (target == null || !quest.target().equalsIgnoreCase(target))) {
                    continue;
                }

                QuestManager.ProgressResult result = questManager.incrementProgress(player.getUniqueId(), quest, amount);
                if (result.justCompleted()) {
                    Bukkit.getScheduler().runTask(plugin, () -> onQuestCompleted(player, quest));
                }
            }
        });
    }

    private void onQuestCompleted(Player player, QuestDefinition quest) {
        battlePassService.addXp(player, quest.xpReward());
        if (quest.reward() != null) {
            RewardGiver.give(player, quest.reward(), economyManager, messages);
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quete", quest.displayName());
        placeholders.put("xp", String.valueOf(quest.xpReward()));
        messages.send(player, "quests.terminee", placeholders);
    }
}
