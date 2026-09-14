package com.mysteriacraft.economy.generator.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.generator.GeneratorManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * /generateur give <joueur> <fer|or|diamant> [quantite] | reload (admin uniquement)
 */
public class GeneratorCommand implements CommandExecutor {

    private final ConfigManager generatorsConfig;
    private final GeneratorManager manager;
    private final MessageManager messages;

    public GeneratorCommand(ConfigManager generatorsConfig, GeneratorManager manager, MessageManager messages) {
        this.generatorsConfig = generatorsConfig;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.generateur.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "generateur.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            generatorsConfig.reload();
            manager.loadConfig();
            messages.send(sender, "generateur.reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            return handleGive(sender, args);
        }

        messages.send(sender, "generateur.usage");
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "generateur.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "general.joueur-introuvable");
            return true;
        }
        GeneratorManager.GeneratorType type = manager.getType(args[2]);
        if (type == null) {
            messages.send(sender, "generateur.type-introuvable");
            return true;
        }
        int quantity = 1;
        if (args.length >= 4) {
            try {
                quantity = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                messages.send(sender, "generateur.usage");
                return true;
            }
        }

        ItemStack item = manager.createGeneratorItem(type, quantity);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("joueur", target.getName());
        placeholders.put("quantite", String.valueOf(quantity));
        placeholders.put("generateur", type.displayName());
        messages.send(sender, "generateur.give-effectue", placeholders);
        return true;
    }
}
