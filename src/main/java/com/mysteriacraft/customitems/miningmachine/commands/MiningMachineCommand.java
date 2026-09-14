package com.mysteriacraft.customitems.miningmachine.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.miningmachine.MiningMachineManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * /machineminiere give <joueur> [quantite] | reload (admin uniquement)
 */
public class MiningMachineCommand implements CommandExecutor {

    private final ConfigManager configManager;
    private final MiningMachineManager manager;
    private final MessageManager messages;

    public MiningMachineCommand(ConfigManager configManager, MiningMachineManager manager, MessageManager messages) {
        this.configManager = configManager;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.machineminiere.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "machine-miniere.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            configManager.reload();
            manager.loadConfig();
            messages.send(sender, "machine-miniere.reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length < 2) {
                messages.send(sender, "machine-miniere.usage");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages.send(sender, "general.joueur-introuvable");
                return true;
            }
            int quantity = 1;
            if (args.length >= 3) {
                try {
                    quantity = Math.max(1, Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    messages.send(sender, "machine-miniere.usage");
                    return true;
                }
            }

            ItemStack item = manager.createMachineItem(quantity);
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            }

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("joueur", target.getName());
            placeholders.put("quantite", String.valueOf(quantity));
            messages.send(sender, "machine-miniere.give-effectue", placeholders);
            return true;
        }

        messages.send(sender, "machine-miniere.usage");
        return true;
    }
}
