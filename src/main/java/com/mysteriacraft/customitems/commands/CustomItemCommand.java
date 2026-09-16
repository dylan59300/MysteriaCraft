package com.mysteriacraft.customitems.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemDefinition;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.customitems.CustomItemService;
import com.mysteriacraft.customitems.gui.CustomItemsGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

/**
 * /customitem give <joueur> <id> [quantite] (admin)
 * /customitem sell <id>
 * /customitem recettes
 * /customitem reload (admin)
 */
public class CustomItemCommand implements CommandExecutor, TabCompleter {

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
        // Sans argument (ou "gui") : catalogue de tous les items custom disponibles.
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.commande-joueur-uniquement");
                return true;
            }
            new CustomItemsGui(player, manager, messages).open();
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

    /** Complete "give"/"sell" avec la liste des ids d'items custom existants (voir custom_items.yml),
     * pour que le joueur voie directement en jeu ce qu'il peut se donner sans avoir a deviner l'id. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(List.of("gui", "give", "sell", "recettes", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return null; // Bukkit complete automatiquement avec les joueurs en ligne.
        }
        if ((args.length == 2 && args[0].equalsIgnoreCase("sell"))
                || (args.length == 3 && args[0].equalsIgnoreCase("give"))) {
            List<String> ids = manager.getItemsSorted().stream().map(CustomItemDefinition::id).toList();
            return filterStartsWith(ids, args[args.length - 1]);
        }
        return new ArrayList<>();
    }

    private static List<String> filterStartsWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }

    private String joinIds() {
        return manager.getItemsSorted().stream().map(CustomItemDefinition::id).collect(Collectors.joining(", "));
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
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("ids", joinIds());
            messages.send(sender, "customitem.introuvable", placeholders);
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
