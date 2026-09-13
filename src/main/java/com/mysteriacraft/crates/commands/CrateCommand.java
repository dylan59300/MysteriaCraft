package com.mysteriacraft.crates.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.crates.Crate;
import com.mysteriacraft.crates.CrateManager;
import com.mysteriacraft.crates.CrateService;
import com.mysteriacraft.crates.gui.CrateListGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public class CrateCommand implements CommandExecutor {

    private final Plugin plugin;
    private final CrateManager crateManager;
    private final CrateService crateService;
    private final MessageManager messages;

    public CrateCommand(Plugin plugin, CrateManager crateManager, CrateService crateService, MessageManager messages) {
        this.plugin = plugin;
        this.crateManager = crateManager;
        this.crateService = crateService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length >= 1) {
            crateService.open(player, args[0]);
            return true;
        }

        openMenu(player);
        return true;
    }

    private void openMenu(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Integer> keyCounts = new HashMap<>();
            for (Crate crate : crateManager.getCratesSorted()) {
                keyCounts.put(crate.id(), crateManager.getKeyCount(player.getUniqueId(), crate.id()));
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    new CrateListGui(player, crateManager, crateService, messages, keyCounts).open());
        });
    }
}
