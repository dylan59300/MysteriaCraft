package fr.vanadia.commands;

import fr.vanadia.Vanadia;
import fr.vanadia.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VanadiaCommand implements CommandExecutor, TabCompleter {

    private final Vanadia plugin;

    public VanadiaCommand(Vanadia plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vanadia.admin")) {
            if (sender instanceof Player player) {
                plugin.getMessageUtil().send(player, "no-permission");
            }
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                if (sender instanceof Player player) {
                    plugin.getMessageUtil().send(player, "reload-success");
                } else {
                    sender.sendMessage("Configuration rechargee!");
                }
            }
            case "give" -> handleGive(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Utilisation: /vanadia give <joueur> <item_id>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Joueur introuvable.");
            return;
        }

        ItemStack item = plugin.getItemManager().createItemStack(args[2]);
        if (item == null) {
            sender.sendMessage("Item inconnu: " + args[2]);
            return;
        }

        target.getInventory().addItem(item);
        sender.sendMessage("Item donne a " + target.getName() + "!");
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Utilisation: /vanadia setlevel <joueur> <niveau>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Joueur introuvable.");
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Niveau invalide.");
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(target.getUniqueId());
        if (data == null) return;

        data.setLevel(level);
        data.setXp(0);
        plugin.getClassManager().applyClassStats(target, data);
        sender.sendMessage("Niveau de " + target.getName() + " mis a " + level + "!");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("=== Vanadia Admin ===");
        sender.sendMessage("/vanadia reload - Recharger la config");
        sender.sendMessage("/vanadia give <joueur> <item> - Donner un item custom");
        sender.sendMessage("/vanadia setlevel <joueur> <niv> - Definir le niveau");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("vanadia.admin")) return List.of();

        if (args.length == 1) {
            return Arrays.asList("reload", "give", "setlevel").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("setlevel"))) {
            return null; // Default player completion
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return plugin.getItemManager().getAllItems().stream()
                    .map(i -> i.getId())
                    .filter(id -> id.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
