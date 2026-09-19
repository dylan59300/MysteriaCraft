package com.mysteriacraft.mobscustom.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.mobscustom.MobManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** /mobcustom spawn <id> [nombre] : fait apparaitre un mob custom a la position de l'admin.
 * /mobcustom reload : recharge mobs_custom.yml. */
public class MobCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager mobsConfig;
    private final MobManager manager;
    private final MessageManager messages;

    public MobCommand(ConfigManager mobsConfig, MobManager manager, MessageManager messages) {
        this.mobsConfig = mobsConfig;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.mobcustom.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            mobsConfig.reload();
            manager.load();
            messages.send(sender, "mobcustom.reload");
            return true;
        }

        if (args.length > 1 && args[0].equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.commande-joueur-uniquement");
                return true;
            }
            MobManager.MobDefinition definition = manager.getMob(args[1]);
            if (definition == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("ids", String.join(", ", manager.getMobIds()));
                messages.send(player, "mobcustom.introuvable", placeholders);
                return true;
            }
            int nombre = args.length > 2 ? parseIntOrDefault(args[2], 1) : 1;
            for (int i = 0; i < Math.max(1, Math.min(nombre, 50)); i++) {
                manager.spawn(player.getLocation(), definition);
            }
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("nombre", String.valueOf(nombre));
            placeholders.put("mob", definition.nom());
            messages.send(player, "mobcustom.spawn-effectue", placeholders);
            return true;
        }

        messages.send(sender, "mobcustom.usage");
        return true;
    }

    private int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("spawn", "reload").stream().filter(o -> o.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return manager.getMobIds().stream().filter(id -> id.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        return List.of();
    }
}
