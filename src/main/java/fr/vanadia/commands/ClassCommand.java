package fr.vanadia.commands;

import fr.vanadia.Vanadia;
import fr.vanadia.classes.ClassType;
import fr.vanadia.player.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClassCommand implements CommandExecutor, TabCompleter {

    private final Vanadia plugin;

    public ClassCommand(Vanadia plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageUtil().sendRaw((Player) sender, plugin.getMessageUtil().getRaw("player-only"));
            return true;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) return true;

        if (args.length == 0) {
            if (data.getClassType() == null) {
                plugin.getMessageUtil().send(player, "class-no-class");
                showAvailableClasses(player);
            } else {
                plugin.getMessageUtil().send(player, "class-info", Map.of(
                        "class", data.getClassType().getColoredName(),
                        "level", String.valueOf(data.getLevel()),
                        "xp", data.getXp() + "/" + data.getXpToNextLevel()
                ));
            }
            return true;
        }

        ClassType classType = ClassType.fromString(args[0]);
        if (classType == null) {
            showAvailableClasses(player);
            return true;
        }

        if (data.getClassType() == classType) {
            plugin.getMessageUtil().send(player, "class-already-chosen",
                    Map.of("class", classType.getColoredName()));
            return true;
        }

        plugin.getClassManager().setClass(player, classType);
        plugin.getMessageUtil().send(player, "class-chosen",
                Map.of("class", classType.getColoredName()));
        return true;
    }

    private void showAvailableClasses(Player player) {
        plugin.getMessageUtil().send(player, "class-list-header");
        for (ClassType type : ClassType.values()) {
            plugin.getMessageUtil().sendRaw(player,
                    " &7- " + type.getColoredName() + " &7: " +
                            "PV:" + (int) type.getBaseHealth() +
                            " ATK:" + (int) type.getBaseDamage() +
                            " DEF:" + (int) type.getBaseDefense());
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.stream(ClassType.values())
                    .map(c -> c.name().toLowerCase())
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
