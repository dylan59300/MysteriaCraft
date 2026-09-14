package com.mysteriacraft.quests;

import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Recoit les evenements de jeu (bloc casse, mob tue, peche, craft, consommation...), fait
 * progresser les quetes correspondantes, et donne automatiquement l'xp de BattlePass + la
 * recompense directe des qu'une quete est terminee (pas de reclamation manuelle).
 */
public class QuestService {

    private final Plugin plugin;
    private final QuestManager questManager;
    private final BattlePassService battlePassService;
    private final RewardGiver rewardGiver;
    private final MessageManager messages;

    public QuestService(Plugin plugin, QuestManager questManager, BattlePassService battlePassService,
                         RewardGiver rewardGiver, MessageManager messages) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.battlePassService = battlePassService;
        this.rewardGiver = rewardGiver;
        this.messages = messages;
    }

    /**
     * A appeler depuis un listener Bukkit (thread principal) a chaque action pouvant faire progresser une quete.
     * Execute volontairement en SYNCHRONE sur le thread appelant (comme le font tous les autres Managers du
     * plugin qui touchent la base SQLite partagee) : la reserver a un thread asynchrone provoquait des acces
     * concurrents a la meme Connection SQLite lorsqu'un autre Manager ecrivait au meme instant (typiquement
     * MachineManager juste avant cet appel lors d'une transformation reussie), ce qui faisait echouer
     * silencieusement l'ecriture de progression (exception SQLite avalee, quete bloquee a 0). Une requete
     * SQLite locale etant de toute facon quasi instantanee, l'impact sur le thread principal est negligeable.
     */
    public void registerProgress(Player player, QuestType type, String target, int amount) {
        for (QuestDefinition quest : questManager.getAllQuests()) {
            if (quest.type() != type) {
                continue;
            }
            if (quest.target() != null && (target == null || !quest.target().equalsIgnoreCase(target))) {
                continue;
            }

            QuestManager.ProgressResult result = questManager.incrementProgress(player.getUniqueId(), quest, amount);
            if (result.justCompleted()) {
                onQuestCompleted(player, quest);
            }
        }
    }

    private void onQuestCompleted(Player player, QuestDefinition quest) {
        battlePassService.addXp(player, quest.xpReward());
        if (quest.reward() != null) {
            rewardGiver.give(player, quest.reward());
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quete", quest.displayName());
        placeholders.put("xp", String.valueOf(quest.xpReward()));
        messages.send(player, "quests.terminee", placeholders);

        showCompletionTitle(player, quest);
    }

    private void showCompletionTitle(Player player, QuestDefinition quest) {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        Component titleText = legacy.deserialize(messages.raw("quests.titre-ligne1"));
        Component subtitleText = legacy.deserialize(messages.raw("quests.titre-ligne2").replace("{quete}", quest.displayName()));

        Title title = Title.title(titleText, subtitleText,
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500)));
        player.showTitle(title);
    }
}
