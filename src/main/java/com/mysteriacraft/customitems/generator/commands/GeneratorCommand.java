package com.mysteriacraft.customitems.generator.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.customitems.generator.GeneratorManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * /generateur give <joueur> <fer|or|diamant> [quantite] | reload (admin uniquement)
 * /generateur liste (accessible a tout joueur, liste SES generateurs actifs)
 */
public class GeneratorCommand implements CommandExecutor {

    private final ConfigManager generatorsConfig;
    private final GeneratorManager manager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public GeneratorCommand(ConfigManager generatorsConfig, GeneratorManager manager,
                             EconomyManager economyManager, MessageManager messages) {
        this.generatorsConfig = generatorsConfig;
        this.manager = manager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "generateur.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("liste")) {
            return handleListe(sender);
        }

        // Le reste (give/reload) est reserve aux admins.
        if (!sender.hasPermission("mysteriacraft.generateur.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            generatorsConfig.reload();
            manager.loadConfig();
            manager.registerRecipes();
            messages.send(sender, "generateur.reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            return handleGive(sender, args);
        }

        messages.send(sender, "generateur.usage");
        return true;
    }

    /** Liste, pour l'expediteur lui-meme, les generateurs actifs qu'il possede (suivi depuis le
     * dernier demarrage du plugin - voir GeneratorManager#countOwnedGenerators). */
    private boolean handleListe(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        int found = 0;
        messages.send(sender, "generateur.liste-titre");
        for (Location location : manager.getActiveGeneratorLocations()) {
            Block block = location.getBlock();
            if (!manager.isGeneratorBlock(block) || !player.getUniqueId().equals(manager.getOwner(block))) {
                continue;
            }
            GeneratorManager.GeneratorType type = manager.getBlockType(block);
            if (type == null) {
                continue;
            }
            double stored = manager.accrue(block);
            found++;
            String coords = location.getWorld() != null ? location.getWorld().getName() : "?";
            coords += " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
            sender.sendMessage(MessageManager.color("&e" + type.displayName() + " &7- " + coords
                    + " &7- &a" + economyManager.format(stored) + " &7/ &a" + economyManager.format(type.storageMax())));
        }

        if (found == 0) {
            messages.send(sender, "generateur.liste-vide");
        }
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
