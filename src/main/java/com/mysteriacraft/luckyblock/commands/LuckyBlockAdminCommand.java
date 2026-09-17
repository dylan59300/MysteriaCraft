package com.mysteriacraft.luckyblock.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.LuckyBlockEditorService;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import com.mysteriacraft.luckyblock.gui.LuckyBlockFamiliesEditorGui;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
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
 * /luckyblockadmin give <joueur> <famille> [quantite] | simulate <famille> <nombre> | reload
 */
public class LuckyBlockAdminCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final ConfigManager luckyBlocksConfig;
    private final LuckyBlockManager manager;
    private final LuckyBlockService service;
    private final LuckyBlockEditorService editorService;
    private final MessageManager messages;

    public LuckyBlockAdminCommand(Plugin plugin, ConfigManager luckyBlocksConfig, LuckyBlockManager manager,
                                   LuckyBlockService service, LuckyBlockEditorService editorService,
                                   MessageManager messages) {
        this.plugin = plugin;
        this.luckyBlocksConfig = luckyBlocksConfig;
        this.manager = manager;
        this.service = service;
        this.editorService = editorService;
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

        if (args[0].equalsIgnoreCase("editeur")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.commande-joueur-uniquement");
                return true;
            }
            new LuckyBlockFamiliesEditorGui(plugin, player, manager, editorService, messages).open();
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
                Map<String, String> notFoundPlaceholders = new HashMap<>();
                notFoundPlaceholders.put("ids", joinIds());
                messages.send(sender, "luckyblock.introuvable", notFoundPlaceholders);
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
                Map<String, String> notFoundPlaceholders = new HashMap<>();
                notFoundPlaceholders.put("ids", joinIds());
                messages.send(sender, "luckyblock.introuvable", notFoundPlaceholders);
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

    /** Complete "give"/"simulate" avec la liste des familles de LuckyBlock existantes (voir
     * luckyblocks.yml), pour que le joueur voie directement en jeu ce qu'il peut se donner. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(List.of("give", "simulate", "reload", "editeur"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return null; // Bukkit complete automatiquement avec les joueurs en ligne.
        }
        if ((args.length == 2 && args[0].equalsIgnoreCase("simulate"))
                || (args.length == 3 && args[0].equalsIgnoreCase("give"))) {
            List<String> ids = manager.getFamiliesSorted().stream().map(LuckyBlockFamily::id).toList();
            return filterStartsWith(ids, args[args.length - 1]);
        }
        return new ArrayList<>();
    }

    private static List<String> filterStartsWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }

    private String joinIds() {
        return manager.getFamiliesSorted().stream().map(LuckyBlockFamily::id).collect(Collectors.joining(", "));
    }
}
