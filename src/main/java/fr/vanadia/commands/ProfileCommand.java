package fr.vanadia.commands;

import fr.vanadia.Vanadia;
import fr.vanadia.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ProfileCommand implements CommandExecutor {

    private final Vanadia plugin;

    public ProfileCommand(Vanadia plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        Player target = player;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                plugin.getMessageUtil().send(player, "player-not-found");
                return true;
            }
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(target.getUniqueId());
        if (data == null) return true;

        String className = data.getClassType() != null ? data.getClassType().getColoredName() : "&7Aucune";

        plugin.getMessageUtil().send(player, "profile-header",
                Map.of("player", target.getName()));
        plugin.getMessageUtil().send(player, "profile-class",
                Map.of("class", className));
        plugin.getMessageUtil().send(player, "profile-level",
                Map.of("level", String.valueOf(data.getLevel())));
        plugin.getMessageUtil().send(player, "profile-xp",
                Map.of("xp", String.valueOf(data.getXp()),
                       "max_xp", String.valueOf(data.getXpToNextLevel())));

        if (data.getClassType() != null) {
            double damage = plugin.getClassManager().calculateDamage(data);
            double defense = plugin.getClassManager().calculateDefense(data);
            plugin.getMessageUtil().send(player, "profile-damage",
                    Map.of("damage", String.format("%.1f", damage)));
            plugin.getMessageUtil().send(player, "profile-defense",
                    Map.of("defense", String.format("%.1f", defense)));
        }

        plugin.getMessageUtil().send(player, "profile-quests",
                Map.of("quests", String.valueOf(data.getQuestsCompleted())));
        plugin.getMessageUtil().send(player, "profile-dungeons",
                Map.of("dungeons", String.valueOf(data.getDungeonsCompleted())));

        return true;
    }
}
