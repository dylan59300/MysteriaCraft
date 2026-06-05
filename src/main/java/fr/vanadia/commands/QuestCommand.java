package fr.vanadia.commands;

import fr.vanadia.Vanadia;
import fr.vanadia.player.PlayerData;
import fr.vanadia.quests.Quest;
import fr.vanadia.quests.QuestProgress;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class QuestCommand implements CommandExecutor, TabCompleter {

    private final Vanadia plugin;

    public QuestCommand(Vanadia plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) return true;

        if (args.length == 0) {
            showActiveQuests(player, data);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> showAvailableQuests(player, data);
            case "accept" -> {
                if (args.length < 2) {
                    plugin.getMessageUtil().sendRaw(player, "&cUtilisation: /quest accept <id>");
                    return true;
                }
                plugin.getQuestManager().acceptQuest(player, args[1]);
            }
            case "abandon" -> {
                if (args.length < 2) {
                    plugin.getMessageUtil().sendRaw(player, "&cUtilisation: /quest abandon <id>");
                    return true;
                }
                plugin.getQuestManager().abandonQuest(player, args[1]);
            }
            case "info" -> {
                if (args.length < 2) {
                    plugin.getMessageUtil().sendRaw(player, "&cUtilisation: /quest info <id>");
                    return true;
                }
                showQuestInfo(player, args[1]);
            }
            default -> showActiveQuests(player, data);
        }
        return true;
    }

    private void showActiveQuests(Player player, PlayerData data) {
        if (data.getActiveQuests().isEmpty()) {
            plugin.getMessageUtil().sendRaw(player, "&7Aucune quete active. Utilisez &e/quest list &7pour en voir.");
            return;
        }

        plugin.getMessageUtil().sendRaw(player, "&6=== Quetes actives ===");
        for (String questId : data.getActiveQuests()) {
            Quest quest = plugin.getQuestManager().getQuest(questId);
            if (quest == null) continue;
            QuestProgress progress = plugin.getQuestManager().getProgress(player.getUniqueId(), questId);
            int prog = progress != null ? progress.getProgress() : 0;
            plugin.getMessageUtil().sendRaw(player,
                    " &7- &e" + quest.getName() + " &7[&a" + prog + "&7/&a" + quest.getGoal() + "&7]");
        }
    }

    private void showAvailableQuests(Player player, PlayerData data) {
        plugin.getMessageUtil().send(player, "quest-list-header");
        for (Quest quest : plugin.getQuestManager().getAllQuests()) {
            if (data.getCompletedQuests().contains(quest.getId())) continue;
            if (data.hasActiveQuest(quest.getId())) continue;

            String levelReq = data.getLevel() >= quest.getRequiredLevel() ? "&a" : "&c";
            plugin.getMessageUtil().sendRaw(player,
                    " &7- &e" + quest.getName() + " " + levelReq + "[Niv." + quest.getRequiredLevel() + "]" +
                            "\n   &7" + quest.getDescription() + " &7| Recompense: &a" + quest.getXpReward() + " XP");
        }
    }

    private void showQuestInfo(Player player, String questId) {
        Quest quest = plugin.getQuestManager().getQuest(questId);
        if (quest == null) {
            plugin.getMessageUtil().sendRaw(player, "&cQuete introuvable: " + questId);
            return;
        }

        plugin.getMessageUtil().sendRaw(player, "&6=== " + quest.getName() + " ===");
        plugin.getMessageUtil().sendRaw(player, "&7" + quest.getDescription());
        plugin.getMessageUtil().sendRaw(player, "&7Type: &e" + quest.getType().name());
        plugin.getMessageUtil().sendRaw(player, "&7Objectif: &e" + quest.getTarget() + " x" + quest.getGoal());
        plugin.getMessageUtil().sendRaw(player, "&7Niveau requis: &e" + quest.getRequiredLevel());
        plugin.getMessageUtil().sendRaw(player, "&7Recompense: &a" + quest.getXpReward() + " XP");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("list", "accept", "abandon", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("info"))) {
            return plugin.getQuestManager().getAllQuests().stream()
                    .map(Quest::getId)
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
