package com.mysteriacraft.customitems.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemDefinition;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.customitems.CustomItemService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /customitem give <joueur> <id> [quantite] (admin)
 * /customitem sell <id>
 * /customitem recettes
 * /customitem reload (admin)
 */
public class CustomItemCommand implements CommandExecutor {

    private final ConfigManager customItemsConfig;
    private final CustomItemManager manager;
    private final CustomItemService service;
    private final MessageManager messages;

    public CustomItemCommand(ConfigManager customItemsConfig, CustomItemManager manager,
                              CustomItemService service, MessageManager messages) {
        this.customItemsConfig = customItemsConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "customitem.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.customitem.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            customItemsConfig.reload();
            manager.loadItems();
            messages.send(sender, "customitem.reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("mysteriacraft.customitem.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            return handleGive(sender, args);
        }

        if (args[0].equalsIgnoreCase("recettes")) {
            sendRecipes(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("sell")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.commande-joueur-uniquement");
                return true;
            }
            if (args.length < 2) {
                messages.send(sender, "customitem.usage");
                return true;
            }
            service.sellAll(player, args[1]);
            return true;
        }

        messages.send(sender, "customitem.usage");
        return true;
    }

    /** Affiche a l'expediteur la forme et les ingredients de chaque item custom craftable. */
    private void sendRecipes(CommandSender sender) {
        List<CustomItemDefinition> craftables = manager.getItemsSorted().stream()
                .filter(CustomItemDefinition::isCraftable)
                .toList();

        if (craftables.isEmpty()) {
            messages.send(sender, "customitem.aucune-recette");
            return;
        }

        messages.send(sender, "customitem.recettes-titre");
        for (CustomItemDefinition definition : craftables) {
            sender.sendMessage(MessageManager.color("&e&l" + definition.displayName() + " &7(id: " + definition.id() + ")"));
            for (String row : definition.recipeShape()) {
                StringBuilder rendered = new StringBuilder();
                for (char c : row.toCharArray()) {
                    if (c == ' ') {
                        rendered.append("&8[ &7- &8] ");
                        continue;
                    }
                    Material ingredient = definition.recipeIngredients().get(c);
                    rendered.append("&8[ &f").append(ingredient != null ? ingredient.name() : "?").append(" &8] ");
                }
                sender.sendMessage(MessageManager.color(rendered.toString()));
            }
        }
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "customitem.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "general.joueur-introuvable");
            return true;
        }
        CustomItemDefinition definition = manager.getItem(args[2]);
        if (definition == null) {
            messages.send(sender, "customitem.introuvable");
            return true;
        }
        int quantity = 1;
        if (args.length >= 4) {
            try {
                quantity = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                messages.send(sender, "customitem.usage");
                return true;
            }
        }

        ItemStack item = manager.createItem(definition, quantity);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("joueur", target.getName());
        placeholders.put("quantite", String.valueOf(quantity));
        placeholders.put("item", definition.displayName());
        messages.send(sender, "customitem.give-effectue", placeholders);
        return true;
    }
}
