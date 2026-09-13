package com.mysteriacraft.crates.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.crates.Crate;
import com.mysteriacraft.crates.CrateManager;
import com.mysteriacraft.crates.CrateReward;
import com.mysteriacraft.crates.CrateService;
import com.mysteriacraft.crates.gui.CrateListGui;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class CrateCommand implements CommandExecutor {

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.00");

    private final Plugin plugin;
    private final CrateManager crateManager;
    private final CrateService crateService;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public CrateCommand(Plugin plugin, CrateManager crateManager, CrateService crateService,
                         EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.crateManager = crateManager;
        this.crateService = crateService;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("preview")) {
            sendPreview(player, args[1]);
            return true;
        }

        if (args.length >= 1) {
            crateService.open(player, args[0]);
            return true;
        }

        openMenu(player);
        return true;
    }

    /** /crate preview <caisse> : affiche la table de loot en % directement dans le chat. */
    private void sendPreview(Player player, String crateId) {
        Crate crate = crateManager.getCrate(crateId);
        if (crate == null) {
            messages.send(player, "crates.introuvable");
            return;
        }

        double totalWeight = crate.totalWeight();
        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("caisse", crate.displayName());
        player.sendMessage(messages.get("crates.preview-titre", titlePlaceholders));

        for (CrateReward reward : crate.rewards()) {
            double percent = totalWeight > 0 ? (reward.chance() / totalWeight) * 100.0 : 0.0;
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("recompense", reward.displayName());
            placeholders.put("pourcentage", PERCENT_FORMAT.format(percent));
            placeholders.put("rarete", reward.rarity().color() + reward.rarity().name());
            player.sendMessage(messages.raw("crates.preview-ligne")
                    .replace("{recompense}", placeholders.get("recompense"))
                    .replace("{pourcentage}", placeholders.get("pourcentage"))
                    .replace("{rarete}", placeholders.get("rarete")));
        }
    }

    private void openMenu(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Integer> keyCounts = new HashMap<>();
            Map<String, Integer> pityCounts = new HashMap<>();
            for (Crate crate : crateManager.getCratesSorted()) {
                keyCounts.put(crate.id(), crateManager.getKeyCount(player.getUniqueId(), crate.id()));
                if (crate.hasPity()) {
                    pityCounts.put(crate.id(), crateManager.getPityCount(player.getUniqueId(), crate.id()));
                }
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    new CrateListGui(plugin, player, crateManager, crateService, economyManager, messages, keyCounts, pityCounts).open());
        });
    }
}
