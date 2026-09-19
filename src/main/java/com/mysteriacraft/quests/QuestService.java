package com.mysteriacraft.quests;

import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    private final EconomyManager economyManager;
    private final MessageManager messages;

    /** Branche apres coup depuis setupCustomItems() (voir MysteriaCraft), car CustomItemManager
     * n'existe pas encore lorsque QuestService est construit (setupQuests() tourne avant). Utilise
     * uniquement par tickCollection(), planifie bien apres la fin de onEnable(). */
    private CustomItemManager customItemManager;

    public QuestService(Plugin plugin, QuestManager questManager, BattlePassService battlePassService,
                         RewardGiver rewardGiver, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.battlePassService = battlePassService;
        this.rewardGiver = rewardGiver;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public void setCustomItemManager(CustomItemManager customItemManager) {
        this.customItemManager = customItemManager;
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
        for (QuestDefinition quest : questManager.getActiveQuestsForPlayer(player.getUniqueId())) {
            if (quest.type() != type) {
                continue;
            }
            if (quest.target() != null && (target == null || !quest.target().equalsIgnoreCase(target))) {
                continue;
            }
            // Condition meteo optionnelle (voir champ "meteo" dans quests.yml) : ne progresse que
            // si le temps du monde du joueur correspond au moment de l'action.
            if (quest.meteo() != null) {
                boolean pluie = player.getWorld().hasStorm();
                if (quest.meteo() == QuestDefinition.WeatherCondition.PLUIE && !pluie) {
                    continue;
                }
                if (quest.meteo() == QuestDefinition.WeatherCondition.CLAIR && pluie) {
                    continue;
                }
            }
            // Quete "contrat" : ne progresse que si explicitement acceptee via /quests accepter.
            if (quest.contrat() && !questManager.isContractAccepted(player.getUniqueId(), quest)) {
                continue;
            }

            QuestManager.ProgressResult result = questManager.incrementProgress(player.getUniqueId(), quest, amount);
            if (result.justCompleted()) {
                onQuestCompleted(player, quest);
            } else if (result.newProgression() > 0) {
                sendProgressActionBar(player, quest, result.newProgression());
            }
        }
    }

    private void sendProgressActionBar(Player player, QuestDefinition quest, int progression) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quete", quest.displayName());
        placeholders.put("progression", String.valueOf(progression));
        placeholders.put("objectif", String.valueOf(quest.objective()));
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                messages.raw("quests.progression-actionbar")
                        .replace("{quete}", quest.displayName())
                        .replace("{progression}", String.valueOf(progression))
                        .replace("{objectif}", String.valueOf(quest.objective()))));
    }

    private void onQuestCompleted(Player player, QuestDefinition quest) {
        boolean boosted = questManager.isBoosted(player.getUniqueId(), quest);
        long xp = boosted ? quest.xpReward() * 2 : quest.xpReward();
        Reward reward = quest.reward();
        if (boosted && reward != null) {
            reward = reward.doubled();
        }

        battlePassService.addXp(player, xp);
        battlePassService.grantFirstQuestOfDayBonusIfEligible(player);
        if (reward != null) {
            rewardGiver.give(player, reward);
        }
        questManager.logCompletion(player.getUniqueId(), quest.displayName(), xp);

        if (quest.period() == QuestPeriod.DAILY) {
            int streak = questManager.registerDailyStreak(player.getUniqueId());
            if (streak > 0 && streak % 7 == 0) {
                battlePassService.addXp(player, 200);
                Map<String, String> streakPlaceholders = new HashMap<>();
                streakPlaceholders.put("streak", String.valueOf(streak));
                messages.send(player, "quests.streak-atteint", streakPlaceholders);
            }
        }
        if (quest.period() != QuestPeriod.PERMANENT) {
            int distinctTypes = questManager.registerWeeklyTypeCompletion(player.getUniqueId(), quest.type());
            int typesRequis = (int) questManager.getWeeklyQuests().stream().map(QuestDefinition::type).distinct().count();
            if (typesRequis > 0 && distinctTypes == typesRequis) {
                battlePassService.addXp(player, 250);
                messages.send(player, "quests.polyvalence-atteinte");
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quete", quest.displayName());
        placeholders.put("xp", String.valueOf(xp));
        messages.send(player, boosted ? "quests.terminee-boostee" : "quests.terminee", placeholders);

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

    // ---- Boost achetable (voir /quests boost) ----

    public boolean buyBoost(Player player, QuestDefinition quest, double price) {
        if (questManager.isBoosted(player.getUniqueId(), quest)) {
            messages.send(player, "quests.deja-boostee");
            return false;
        }
        if (!economyManager.has(player.getUniqueId(), price) || !economyManager.withdraw(player.getUniqueId(), price)) {
            messages.send(player, "quests.fonds-insuffisants");
            return false;
        }
        questManager.activateBoost(player.getUniqueId(), quest);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quete", quest.displayName());
        placeholders.put("prix", economyManager.format(price));
        messages.send(player, "quests.boost-active", placeholders);
        return true;
    }

    // ---- Exploration + collection (verifiees periodiquement, voir MysteriaCraft) ----

    /** A appeler periodiquement (toutes les quelques secondes) : verifie le biome courant de
     * chaque joueur en ligne et fait progresser les quetes EXPLORE_BIOME sur un NOUVEAU biome. */
    public void tickExploration() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String biome = player.getLocation().getBlock().getBiome().name();
            if (questManager.markBiomeVisitedIfNew(player.getUniqueId(), biome)) {
                registerProgress(player, QuestType.EXPLORE_BIOME, biome, 1);
            }
        }
    }

    /** A appeler periodiquement : compte les objets custom DIFFERENTS possedes simultanement par
     * chaque joueur en ligne, et fait progresser les quetes COLLECT_DISTINCT_CUSTOM_ITEMS. */
    public void tickCollection() {
        if (customItemManager == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<String> distinctIds = new HashSet<>();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null) {
                    continue;
                }
                String id = customItemManager.getCustomItemId(item);
                if (id != null) {
                    distinctIds.add(id);
                }
            }
            for (QuestDefinition quest : questManager.getActiveQuestsForPlayer(player.getUniqueId())) {
                if (quest.type() != QuestType.COLLECT_DISTINCT_CUSTOM_ITEMS) {
                    continue;
                }
                if (quest.contrat() && !questManager.isContractAccepted(player.getUniqueId(), quest)) {
                    continue;
                }
                QuestManager.ProgressResult result = questManager.setProgressIfHigher(player.getUniqueId(), quest, distinctIds.size());
                if (result.justCompleted()) {
                    onQuestCompleted(player, quest);
                }
            }
        }
    }
}
