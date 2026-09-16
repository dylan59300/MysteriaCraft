package com.mysteriacraft.homes.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.homes.HomeManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /home [nom] : teleporte vers un home (par defaut "default").
 * /home set [nom] : sauvegarde la position actuelle comme home (par defaut "default").
 * /home del <nom> : supprime un home.
 * /home liste : liste vos homes et votre limite actuelle.
 */
public class HomeCommand implements CommandExecutor, TabCompleter {

    private static final String DEFAULT_NAME = "default";

    private final Plugin plugin;
    private final HomeManager manager;
    private final MessageManager messages;

    public HomeCommand(Plugin plugin, HomeManager manager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        UUID uuid = player.getUniqueId();

        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            String nom = args.length > 1 ? args[1] : DEFAULT_NAME;
            setHome(player, uuid, nom);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("del")) {
            if (args.length < 2) {
                messages.send(player, "home.usage-del");
                return true;
            }
            delHome(player, uuid, args[1]);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("liste")) {
            listHomes(player, uuid);
            return true;
        }

        String nom = args.length > 0 ? args[0] : DEFAULT_NAME;
        teleportHome(player, uuid, nom);
        return true;
    }

    private void teleportHome(Player player, UUID uuid, String nom) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location location = manager.getHome(uuid, nom);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (location == null) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("nom", nom);
                    messages.send(player, "home.introuvable", placeholders);
                    return;
                }
                player.teleport(location);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("nom", nom);
                messages.send(player, "home.teleporte", placeholders);
            });
        });
    }

    private void setHome(Player player, UUID uuid, String nom) {
        Location location = player.getLocation();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean exists = manager.exists(uuid, nom);
            int count = manager.countHomes(uuid);
            int limit = manager.getLimit(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!exists && count >= limit) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("limite", String.valueOf(limit));
                    messages.send(player, "home.limite-atteinte", placeholders);
                    return;
                }
                manager.setHome(uuid, nom, location);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("nom", nom);
                messages.send(player, "home.defini", placeholders);
            });
        });
    }

    private void delHome(Player player, UUID uuid, String nom) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean deleted = manager.deleteHome(uuid, nom);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("nom", nom);
                messages.send(player, deleted ? "home.supprime" : "home.introuvable", placeholders);
            });
        });
    }

    private void listHomes(Player player, UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<HomeManager.Home> homes = manager.getHomes(uuid);
            int limit = manager.getLimit(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("nombre", String.valueOf(homes.size()));
                placeholders.put("limite", String.valueOf(limit));
                placeholders.put("noms", homes.isEmpty() ? "-" :
                        homes.stream().map(HomeManager.Home::nom).collect(Collectors.joining(", ")));
                messages.send(player, "home.liste", placeholders);
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("set", "del", "liste").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return List.of();
    }
}
