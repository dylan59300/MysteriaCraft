package com.mysteriacraft.hybride.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.hybride.HybridGeneratorManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** /generateurhybride give <joueur> <type> [quantite] : donne un generateur hybride (admin).
 * /generateurhybride reload : recharge hybride_generateur.yml (admin). */
public class HybridGeneratorCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager hybrideConfig;
    private final HybridGeneratorManager manager;
    private final MessageManager messages;

    public HybridGeneratorCommand(ConfigManager hybrideConfig, HybridGeneratorManager manager, MessageManager messages) {
        this.hybrideConfig = hybrideConfig;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "generateurhybride.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.generateurhybride.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            hybrideConfig.reload();
            manager.loadTypes();
            messages.send(sender, "generateurhybride.reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("mysteriacraft.generateurhybride.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            if (args.length < 3) {
                messages.send(sender, "generateurhybride.usage");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            HybridGeneratorManager.GeneratorType type = manager.getType(args[2]);
            if (type == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("ids", String.join(", ", manager.getTypeIds()));
                messages.send(sender, "generateurhybride.type-introuvable", placeholders);
                return true;
            }
            int quantity = args.length > 3 ? parseIntOrDefault(args[3], 1) : 1;
            ItemStack item = manager.createGeneratorItem(type, quantity);
            if (target.isOnline() && target.getPlayer() != null) {
                Player onlinePlayer = target.getPlayer();
                Map<Integer, ItemStack> leftovers = onlinePlayer.getInventory().addItem(item);
                leftovers.values().forEach(leftover -> onlinePlayer.getWorld().dropItemNaturally(onlinePlayer.getLocation(), leftover));
            }
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("joueur", args[1]);
            placeholders.put("quantite", String.valueOf(quantity));
            placeholders.put("type", type.nom());
            messages.send(sender, "generateurhybride.give-effectue", placeholders);
            return true;
        }

        messages.send(sender, "generateurhybride.usage");
        return true;
    }

    private int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give", "reload").stream().filter(o -> o.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return manager.getTypeIds().stream().filter(id -> id.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
