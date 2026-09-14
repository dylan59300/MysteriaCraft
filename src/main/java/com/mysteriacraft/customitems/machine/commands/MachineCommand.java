package com.mysteriacraft.customitems.machine.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.machine.MachineManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * /machine give <joueur> [quantite] | reload (admin uniquement)
 */
public class MachineCommand implements CommandExecutor {

    private final ConfigManager customItemsConfig;
    private final MachineManager manager;
    private final MessageManager messages;

    public MachineCommand(ConfigManager customItemsConfig, MachineManager manager, MessageManager messages) {
        this.customItemsConfig = customItemsConfig;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.machine.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "machine.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            customItemsConfig.reload();
            manager.loadConfig();
            messages.send(sender, "machine.reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length < 2) {
                messages.send(sender, "machine.usage");
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
                    messages.send(sender, "machine.usage");
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
            messages.send(sender, "machine.give-effectue", placeholders);
            return true;
        }

        messages.send(sender, "machine.usage");
        return true;
    }
}
