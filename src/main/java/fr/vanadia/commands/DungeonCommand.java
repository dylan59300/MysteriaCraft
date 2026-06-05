package fr.vanadia.commands;

import fr.vanadia.Vanadia;
import fr.vanadia.dungeons.Dungeon;
import fr.vanadia.player.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class DungeonCommand implements CommandExecutor, TabCompleter {

    private final Vanadia plugin;

    public DungeonCommand(Vanadia plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) return true;

        if (args.length == 0) {
            showDungeons(player, data);
            return true;
        }

        if (args[0].equalsIgnoreCase("leave")) {
            plugin.getDungeonManager().leaveDungeon(player);
            return true;
        }

        plugin.getDungeonManager().enterDungeon(player, args[0]);
        return true;
    }

    private void showDungeons(Player player, PlayerData data) {
        plugin.getMessageUtil().sendRaw(player, "&6=== Donjons disponibles ===");
        for (Dungeon dungeon : plugin.getDungeonManager().getAllDungeons()) {
            String levelReq = data.getLevel() >= dungeon.getMinLevel() ? "&a" : "&c";
            plugin.getMessageUtil().sendRaw(player,
                    " &7- &e" + dungeon.getDisplayName() + " " +
                            levelReq + "[Niv." + dungeon.getMinLevel() + "] " +
                            "&7Max: " + dungeon.getMaxPlayers() + " joueurs");
        }
        plugin.getMessageUtil().sendRaw(player, "&7Utilisation: &e/dungeon <nom>");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = plugin.getDungeonManager().getAllDungeons().stream()
                    .map(Dungeon::getId)
                    .filter(id -> id.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            completions.add("leave");
            return completions;
        }
        return List.of();
    }
}
