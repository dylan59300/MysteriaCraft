package com.mysteriacraft.luckyblock.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * /luckyblockadmin give <joueur> <famille> [quantite] | simulate <famille> <nombre> | reload
 */
public class LuckyBlockAdminCommand implements CommandExecutor {

    private final ConfigManager luckyBlocksConfig;
    private final LuckyBlockManager manager;
    private final LuckyBlockService service;
    private final MessageManager messages;

    public LuckyBlockAdminCommand(ConfigManager luckyBlocksConfig, LuckyBlockManager manager,
                                   LuckyBlockService service, MessageManager messages) {
        this.luckyBlocksConfig = luckyBlocksConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.luckyblock.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "luckyblock.admin-usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            luckyBlocksConfig.reload();
            manager.loadFamilies();
            manager.registerRecipes();
            messages.send(sender, "luckyblock.reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length < 3) {
                messages.send(sender, "luckyblock.admin-usage");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages.send(sender, "general.joueur-introuvable");
                return true;
            }
            LuckyBlockFamily family = manager.getFamily(args[2]);
            if (family == null) {
                messages.send(sender, "luckyblock.introuvable");
                return true;
            }
            int quantity = 1;
            if (args.length >= 4) {
                try {
                    quantity = Math.max(1, Integer.parseInt(args[3]));
                } catch (NumberFormatException e) {
                    messages.send(sender, "luckyblock.admin-usage");
                    return true;
                }
            }

            ItemStack item = manager.createItem(family, quantity);
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            }

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("joueur", target.getName());
            placeholders.put("quantite", String.valueOf(quantity));
            placeholders.put("famille", family.displayName());
            messages.send(sender, "luckyblock.give-effectue", placeholders);
            return true;
        }

        if (args[0].equalsIgnoreCase("simulate")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.commande-joueur-uniquement");
                return true;
            }
            if (args.length < 3) {
                messages.send(sender, "luckyblock.admin-usage");
                return true;
            }
            LuckyBlockFamily family = manager.getFamily(args[1]);
            if (family == null) {
                messages.send(sender, "luckyblock.introuvable");
                return true;
            }
            int count;
            try {
                count = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                messages.send(sender, "luckyblock.admin-usage");
                return true;
            }
            service.simulate(player, family, count);
            return true;
        }

        messages.send(sender, "luckyblock.admin-usage");
        return true;
    }
}
