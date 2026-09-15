package com.mysteriacraft.island.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.island.IslandManager;
import com.mysteriacraft.island.IslandService;
import com.mysteriacraft.island.gui.IslandMembersGui;
import com.mysteriacraft.quests.QuestDefinition;
import com.mysteriacraft.quests.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /ile create | home | spawn | sethome | delete confirm | invite <joueur> | accept <joueur> |
 *      kick <joueur> | membres | visit <joueur> | upgrade | niveau | top [page]
 * /ile reload | tp <joueur> (admin uniquement)
 */
public class IslandCommand implements CommandExecutor {

    private static final int TOP_PER_PAGE = 10;

    private final ConfigManager islandsConfig;
    private final IslandManager manager;
    private final IslandService service;
    private final QuestManager questManager;
    private final MessageManager messages;

    public IslandCommand(ConfigManager islandsConfig, IslandManager manager, IslandService service,
                          QuestManager questManager, MessageManager messages) {
        this.islandsConfig = islandsConfig;
        this.manager = manager;
        this.service = service;
        this.questManager = questManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "ile.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("top")) {
            sendTop(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.island.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            islandsConfig.reload();
            manager.loadConfig();
            messages.send(sender, "ile.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> service.create(player);
            case "home" -> {
                IslandManager.Island island = manager.getIsland(player.getUniqueId());
                if (island == null) {
                    messages.send(player, "ile.aucune");
                } else {
                    service.teleportToIsland(player, island);
                }
            }
            case "spawn" -> service.teleportToWorldSpawn(player);
            case "sethome" -> service.setHome(player);
            case "visit" -> {
                if (args.length < 2) {
                    messages.send(player, "ile.usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(player, "general.joueur-introuvable");
                    return true;
                }
                service.visit(player, target);
            }
            case "membres" -> {
                IslandManager.Island island = manager.getIsland(player.getUniqueId());
                if (island == null) {
                    messages.send(player, "ile.aucune");
                } else {
                    new IslandMembersGui(player, island, service, messages).open();
                }
            }
            case "delete" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    messages.send(player, "ile.delete-confirmation");
                    return true;
                }
                service.delete(player);
            }
            case "invite" -> {
                if (args.length < 2) {
                    messages.send(player, "ile.usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(player, "general.joueur-introuvable");
                    return true;
                }
                service.invite(player, target);
            }
            case "accept" -> {
                if (args.length < 2) {
                    messages.send(player, "ile.usage");
                    return true;
                }
                Player owner = Bukkit.getPlayerExact(args[1]);
                if (owner == null) {
                    messages.send(player, "general.joueur-introuvable");
                    return true;
                }
                service.acceptInvite(player, owner);
            }
            case "kick" -> {
                if (args.length < 2) {
                    messages.send(player, "ile.usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(player, "general.joueur-introuvable");
                    return true;
                }
                if (service.kickMember(player, target.getUniqueId())) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("joueur", target.getName());
                    messages.send(player, "ile.membre-exclu", placeholders);
                } else {
                    messages.send(player, "ile.pas-membre");
                }
            }
            case "upgrade" -> service.upgrade(player);
            case "niveau" -> sendLevel(player);
            case "tp" -> {
                if (!player.hasPermission("mysteriacraft.island.admin")) {
                    messages.send(player, "general.pas-de-permission");
                    return true;
                }
                if (args.length < 2) {
                    messages.send(player, "ile.usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(player, "general.joueur-introuvable");
                    return true;
                }
                IslandManager.Island island = manager.getIsland(target.getUniqueId());
                if (island == null) {
                    messages.send(player, "ile.aucune-pour-joueur");
                    return true;
                }
                service.teleportToIsland(player, island);
            }
            default -> messages.send(player, "ile.usage");
        }
        return true;
    }

    private void sendLevel(Player player) {
        IslandManager.Island island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            messages.send(player, "ile.aucune");
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("niveau", String.valueOf(island.value()));
        placeholders.put("taille", String.valueOf(island.size()));
        messages.send(player, "ile.niveau-info", placeholders);

        // Progression des quetes liees au module Iles (evite d'avoir a ouvrir /quests separement).
        for (QuestDefinition quest : questManager.getAllQuests()) {
            if (!quest.type().name().startsWith("ISLAND_") || !quest.isActiveNow()) {
                continue;
            }
            QuestManager.ProgressSnapshot progress = questManager.getProgress(player.getUniqueId(), quest);
            Map<String, String> questPlaceholders = new HashMap<>();
            questPlaceholders.put("quete", quest.displayName());
            questPlaceholders.put("progression", String.valueOf(progress.progression()));
            questPlaceholders.put("objectif", String.valueOf(quest.objective()));
            messages.send(player, progress.complete() ? "ile.niveau-quete-complete" : "ile.niveau-quete-progression", questPlaceholders);
        }
    }

    private void sendTop(CommandSender sender, String[] args) {
        int page = 0;
        if (args.length >= 2) {
            try {
                page = Math.max(0, Integer.parseInt(args[1]) - 1);
            } catch (NumberFormatException ignored) {
                // Page invalide : retombe sur la premiere page.
            }
        }

        List<IslandManager.Island> sorted = manager.getIslandsSortedByValue();
        if (sorted.isEmpty()) {
            messages.send(sender, "ile.top-vide");
            return;
        }

        int maxPage = Math.max(1, (int) Math.ceil(sorted.size() / (double) TOP_PER_PAGE));
        page = Math.min(page, maxPage - 1);

        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("page", String.valueOf(page + 1));
        titlePlaceholders.put("max", String.valueOf(maxPage));
        messages.send(sender, "ile.top-titre", titlePlaceholders);

        int start = page * TOP_PER_PAGE;
        int end = Math.min(sorted.size(), start + TOP_PER_PAGE);
        for (int i = start; i < end; i++) {
            IslandManager.Island island = sorted.get(i);
            String name = Bukkit.getOfflinePlayer(island.owner()).getName();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("rang", String.valueOf(i + 1));
            placeholders.put("joueur", name != null ? name : island.owner().toString());
            placeholders.put("niveau", String.valueOf(island.value()));
            messages.send(sender, "ile.top-ligne", placeholders);
        }
    }
}
