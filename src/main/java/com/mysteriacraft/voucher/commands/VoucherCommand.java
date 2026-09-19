package com.mysteriacraft.voucher.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.voucher.VoucherDefinition;
import com.mysteriacraft.voucher.VoucherEditorService;
import com.mysteriacraft.voucher.VoucherManager;
import com.mysteriacraft.voucher.VoucherService;
import com.mysteriacraft.voucher.gui.VoucherAdminListGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** /voucher give <joueur> <id> [quantite] : donne un Voucher a un joueur.
 * /voucher list : liste les types de Voucher disponibles.
 * /voucher reload : recharge voucher.yml.
 * /voucher admin : ouvre l'editeur en jeu (voir VoucherAdminListGui). */
public class VoucherCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager voucherConfig;
    private final VoucherManager manager;
    private final VoucherService service;
    private final VoucherEditorService editorService;
    private final MessageManager messages;

    public VoucherCommand(ConfigManager voucherConfig, VoucherManager manager, VoucherService service,
                           VoucherEditorService editorService, MessageManager messages) {
        this.voucherConfig = voucherConfig;
        this.manager = manager;
        this.service = service;
        this.editorService = editorService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.voucher.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            voucherConfig.reload();
            manager.load();
            messages.send(sender, "voucher.reload");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.commande-joueur-uniquement");
                return true;
            }
            new VoucherAdminListGui(player, manager, editorService, messages).open();
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            List<VoucherDefinition> vouchers = manager.getVouchersSorted();
            sender.sendMessage(messages.raw("voucher.liste-titre"));
            if (vouchers.isEmpty()) {
                sender.sendMessage(messages.raw("voucher.liste-vide"));
            }
            for (VoucherDefinition definition : vouchers) {
                sender.sendMessage(messages.raw("voucher.liste-entree")
                        .replace("{id}", definition.id()).replace("{nom}", definition.nom()));
            }
            return true;
        }

        if (args.length > 1 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                messages.send(sender, "voucher.joueur-introuvable");
                return true;
            }
            if (args.length < 3) {
                messages.send(sender, "voucher.usage-give");
                return true;
            }
            VoucherDefinition definition = manager.getVoucher(args[2]);
            if (definition == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("id", args[2]);
                messages.send(sender, "voucher.introuvable-id", placeholders);
                return true;
            }
            int quantite = args.length > 3 ? parseIntOrDefault(args[3], 1) : 1;
            if (quantite <= 0) {
                messages.send(sender, "voucher.quantite-invalide");
                return true;
            }
            service.give(target, definition, quantite);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("quantite", String.valueOf(quantite));
            placeholders.put("nom", definition.nom());
            placeholders.put("joueur", target.getName());
            messages.send(sender, "voucher.donne", placeholders);
            return true;
        }

        messages.send(sender, "voucher.usage-give");
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
            return List.of("give", "list", "reload", "admin").stream()
                    .filter(o -> o.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(nom -> nom.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return manager.getVouchersSorted().stream().map(VoucherDefinition::id)
                    .filter(id -> id.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        return List.of();
    }
}
