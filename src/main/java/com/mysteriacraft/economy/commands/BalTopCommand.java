package com.mysteriacraft.economy.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BalTopCommand implements CommandExecutor {

    private static final int PAGE_SIZE = 10;

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public BalTopCommand(Plugin plugin, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        final int finalPage = page;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int total = economyManager.countAccounts();
            int maxPage = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
            int safePage = Math.min(finalPage, maxPage);
            int offset = (safePage - 1) * PAGE_SIZE;
            List<EconomyManager.TopEntry> top = economyManager.getTop(PAGE_SIZE, offset);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (top.isEmpty()) {
                    messages.send(sender, "economie.baltop-vide");
                    return;
                }
                Map<String, String> titlePlaceholders = new HashMap<>();
                titlePlaceholders.put("page", String.valueOf(safePage));
                titlePlaceholders.put("max", String.valueOf(maxPage));
                sender.sendMessage(messages.get("economie.baltop-titre", titlePlaceholders));

                int rang = offset + 1;
                for (EconomyManager.TopEntry entry : top) {
                    Map<String, String> linePlaceholders = new HashMap<>();
                    linePlaceholders.put("rang", String.valueOf(rang));
                    linePlaceholders.put("joueur", entry.nom());
                    linePlaceholders.put("montant", economyManager.format(entry.solde()));
                    String line = messages.raw("economie.baltop-ligne");
                    for (Map.Entry<String, String> placeholder : linePlaceholders.entrySet()) {
                        line = line.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
                    }
                    sender.sendMessage(line);
                    rang++;
                }
            });
        });
        return true;
    }
}
