package com.mysteriacraft.luckyblock.generator.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.generator.LuckyBlockGeneratorManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
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
 * /generateurlb give <joueur> <famille> [quantite] | reload (admin uniquement)
 * /generateurlb liste (accessible a tout joueur, liste SES generateurs actifs)
 */
public class LuckyBlockGeneratorCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final LuckyBlockGeneratorManager manager;
    private final LuckyBlockManager luckyBlockManager;
    private final MessageManager messages;

    public LuckyBlockGeneratorCommand(ConfigManager configManager, LuckyBlockGeneratorManager manager,
                                       LuckyBlockManager luckyBlockManager, MessageManager messages) {
        this.configManager = configManager;
        this.manager = manager;
        this.luckyBlockManager = luckyBlockManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "generateurlb.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("liste")) {
            return handleListe(sender);
        }

        if (!sender.hasPermission("mysteriacraft.generateurlb.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            configManager.reload();
            manager.loadConfig();
            manager.registerRecipe();
            messages.send(sender, "generateurlb.reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            return handleGive(sender, args);
        }

        messages.send(sender, "generateurlb.usage");
        return true;
    }

    /** Liste, pour l'expediteur lui-meme, les Generateurs de Lucky Block actifs qu'il possede. */
    private boolean handleListe(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        int found = 0;
        messages.send(sender, "generateurlb.liste-titre");
        for (Location location : manager.getActiveGeneratorLocations()) {
            Block block = location.getBlock();
            if (!manager.isGeneratorBlock(block) || !player.getUniqueId().equals(manager.getOwner(block))) {
                continue;
            }
            LuckyBlockFamily family = manager.getFamily(block);
            found++;
            String coords = (location.getWorld() != null ? location.getWorld().getName() : "?")
                    + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
            sender.sendMessage(MessageManager.color("&d" + (family != null ? family.displayName() : "?")
                    + " &7- " + coords + " &7- &b" + manager.getFuel(block) + " &7carburant"));
        }

        if (found == 0) {
            messages.send(sender, "generateurlb.liste-vide");
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "generateurlb.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "general.joueur-introuvable");
            return true;
        }
        LuckyBlockFamily family = luckyBlockManager.getFamily(args[2]);
        if (family == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("ids", joinFamilyIds());
            messages.send(sender, "luckyblock.introuvable", placeholders);
            return true;
        }
        int quantity = 1;
        if (args.length >= 4) {
            try {
                quantity = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                messages.send(sender, "generateurlb.usage");
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
        messages.send(sender, "generateurlb.give-effectue", placeholders);
        return true;
    }

    /** Complete "give" avec la liste des familles de LuckyBlock existantes, pour que le joueur
     * voie directement en jeu ce qu'il peut se donner sans avoir a deviner l'id. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(List.of("liste", "give", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return null; // Bukkit complete automatiquement avec les joueurs en ligne.
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = luckyBlockManager.getFamiliesSorted().stream().map(LuckyBlockFamily::id).toList();
            return filterStartsWith(ids, args[2]);
        }
        return new ArrayList<>();
    }

    private static List<String> filterStartsWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }

    private String joinFamilyIds() {
        return luckyBlockManager.getFamiliesSorted().stream().map(LuckyBlockFamily::id).collect(Collectors.joining(", "));
    }
}
