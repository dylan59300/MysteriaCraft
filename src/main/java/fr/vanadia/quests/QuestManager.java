package fr.vanadia.quests;

import fr.vanadia.Vanadia;
import fr.vanadia.player.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class QuestManager {

    private final Vanadia plugin;
    private final Map<String, Quest> quests;
    private final Map<UUID, Map<String, QuestProgress>> playerProgress;
    private static final int MAX_ACTIVE_QUESTS = 3;

    public QuestManager(Vanadia plugin) {
        this.plugin = plugin;
        this.quests = new LinkedHashMap<>();
        this.playerProgress = new HashMap<>();
        loadQuests();
    }

    private void loadQuests() {
        quests.put("chasse-zombies", new Quest(
                "chasse-zombies",
                "Chasse aux Zombies",
                "Eliminez 10 zombies",
                Quest.QuestType.KILL_MOB,
                1, "ZOMBIE", 10, 50,
                List.of("IRON_SWORD:1", "BREAD:5")
        ));

        quests.put("collecte-bois", new Quest(
                "collecte-bois",
                "Bucheron Debutant",
                "Collectez 32 buches de bois",
                Quest.QuestType.COLLECT_ITEM,
                1, "OAK_LOG", 32, 30,
                List.of("IRON_AXE:1")
        ));

        quests.put("tueur-squelettes", new Quest(
                "tueur-squelettes",
                "Tueur de Squelettes",
                "Eliminez 15 squelettes",
                Quest.QuestType.KILL_MOB,
                3, "SKELETON", 15, 75,
                List.of("BOW:1", "ARROW:32")
        ));

        quests.put("mineur-diamant", new Quest(
                "mineur-diamant",
                "Mineur de Diamant",
                "Collectez 5 diamants",
                Quest.QuestType.COLLECT_ITEM,
                5, "DIAMOND", 5, 100,
                List.of("DIAMOND_PICKAXE:1")
        ));

        quests.put("chasse-creepers", new Quest(
                "chasse-creepers",
                "Chasseur de Creepers",
                "Eliminez 8 creepers",
                Quest.QuestType.KILL_MOB,
                5, "CREEPER", 8, 80,
                List.of("TNT:5", "GOLDEN_APPLE:2")
        ));

        quests.put("forgeron", new Quest(
                "forgeron",
                "Apprenti Forgeron",
                "Fabriquez 3 epees en fer",
                Quest.QuestType.CRAFT,
                3, "IRON_SWORD", 3, 60,
                List.of("IRON_INGOT:16")
        ));

        quests.put("enderman-hunter", new Quest(
                "enderman-hunter",
                "Chasseur d'Endermen",
                "Eliminez 5 endermen",
                Quest.QuestType.KILL_MOB,
                10, "ENDERMAN", 5, 150,
                List.of("ENDER_PEARL:8", "DIAMOND:2")
        ));
    }

    public void reload() {
        quests.clear();
        loadQuests();
    }

    public Quest getQuest(String id) {
        return quests.get(id);
    }

    public Collection<Quest> getAllQuests() {
        return quests.values();
    }

    public boolean acceptQuest(Player player, String questId) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) return false;

        Quest quest = quests.get(questId);
        if (quest == null) return false;

        if (data.getActiveQuests().size() >= MAX_ACTIVE_QUESTS) {
            plugin.getMessageUtil().send(player, "quest-max-active");
            return false;
        }

        if (data.hasActiveQuest(questId)) {
            plugin.getMessageUtil().send(player, "quest-already-active");
            return false;
        }

        if (data.getLevel() < quest.getRequiredLevel()) return false;

        data.addActiveQuest(questId);
        playerProgress.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(questId, new QuestProgress(player.getUniqueId(), questId));

        plugin.getMessageUtil().send(player, "quest-accepted",
                Map.of("quest", quest.getName()));
        return true;
    }

    public void abandonQuest(Player player, String questId) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        Quest quest = quests.get(questId);
        if (quest == null) return;

        data.removeActiveQuest(questId);
        Map<String, QuestProgress> progress = playerProgress.get(player.getUniqueId());
        if (progress != null) {
            progress.remove(questId);
        }

        plugin.getMessageUtil().send(player, "quest-abandoned",
                Map.of("quest", quest.getName()));
    }

    public void updateProgress(Player player, Quest.QuestType type, String target, int amount) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        Map<String, QuestProgress> progress = playerProgress.get(player.getUniqueId());
        if (progress == null) return;

        for (String questId : new ArrayList<>(data.getActiveQuests())) {
            Quest quest = quests.get(questId);
            if (quest == null || quest.getType() != type) continue;
            if (!quest.getTarget().equalsIgnoreCase(target)) continue;

            QuestProgress qp = progress.get(questId);
            if (qp == null) continue;

            qp.increment(amount);

            if (qp.getProgress() >= quest.getGoal()) {
                completeQuest(player, quest);
            } else {
                plugin.getMessageUtil().send(player, "quest-progress",
                        Map.of("quest", quest.getName(),
                               "progress", String.valueOf(qp.getProgress()),
                               "goal", String.valueOf(quest.getGoal())));
            }
        }
    }

    private void completeQuest(Player player, Quest quest) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        data.removeActiveQuest(quest.getId());
        data.addCompletedQuest(quest.getId());
        data.incrementQuestsCompleted();

        Map<String, QuestProgress> progress = playerProgress.get(player.getUniqueId());
        if (progress != null) {
            progress.remove(quest.getId());
        }

        boolean leveledUp = data.addXp(quest.getXpReward());
        if (leveledUp) {
            plugin.getMessageUtil().send(player, "level-up",
                    Map.of("level", String.valueOf(data.getLevel())));
            plugin.getClassManager().applyClassStats(player, data);
        }

        for (String reward : quest.getItemRewards()) {
            String[] parts = reward.split(":");
            Material material = Material.valueOf(parts[0]);
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            player.getInventory().addItem(new ItemStack(material, amount));
        }

        plugin.getMessageUtil().send(player, "quest-completed",
                Map.of("quest", quest.getName(),
                       "reward", quest.getXpReward() + " XP"));
    }

    public QuestProgress getProgress(UUID uuid, String questId) {
        Map<String, QuestProgress> progress = playerProgress.get(uuid);
        if (progress == null) return null;
        return progress.get(questId);
    }
}
