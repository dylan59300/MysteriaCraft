package com.mysteriacraft.kit.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.kit.KitManager;
import com.mysteriacraft.kit.KitService;
import com.mysteriacraft.kit.gui.KitGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** /kit : ouvre le menu des kits. /kit <id> : recupere directement ce kit. /kit reload : admin. */
public class KitCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final ConfigManager kitsConfig;
    private final KitManager manager;
    private final KitService service;
    private final MessageManager messages;

    public KitCommand(Plugin plugin, ConfigManager kitsConfig, KitManager manager, KitService service, MessageManager messages) {
        this.plugin = plugin;
        this.kitsConfig = kitsConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.kit.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            kitsConfig.reload();
            manager.loadKits();
            messages.send(sender, "kit.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length > 0) {
            service.claim(player, args[0]);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Long> remainingByKitId = new HashMap<>();
            for (KitManager.Kit kit : manager.getKitsSorted()) {
                remainingByKitId.put(kit.id(), manager.getRemainingCooldownMillis(player.getUniqueId(), kit));
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    new KitGui(player, manager, service, messages, remainingByKitId).open();
                }
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(manager.getKitsSorted().stream().map(KitManager.Kit::id).collect(Collectors.toList()));
        if (sender.hasPermission("mysteriacraft.kit.admin")) {
            options.add("reload");
        }
        String prefix = args[0].toLowerCase();
        return options.stream().filter(option -> option.startsWith(prefix)).collect(Collectors.toList());
    }
}
