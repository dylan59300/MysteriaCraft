package com.mysteriacraft.shop.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.shop.PromotionManager;
import com.mysteriacraft.shop.ShopManager;
import com.mysteriacraft.shop.ShopService;
import com.mysteriacraft.shop.gui.ShopCategoriesGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * /boutique : ouvre le menu de la Boutique.
 * /boutique code <code> : active un code promo pour le prochain achat.
 * /boutique jetons : affiche le solde de Jetons Boutique.
 * /boutique convertir <argent|jetons> <montant> : convertit dans l'autre monnaie.
 * /boutique reload : recharge boutique.yml + promotions.yml (admin).
 */
public class ShopCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ConfigManager shopConfig;
    private final ConfigManager promotionsConfig;
    private final ShopManager manager;
    private final ShopService service;
    private final PromotionManager promotionManager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public ShopCommand(Plugin plugin, ConfigManager shopConfig, ConfigManager promotionsConfig, ShopManager manager,
                        ShopService service, PromotionManager promotionManager, EconomyManager economyManager,
                        MessageManager messages) {
        this.plugin = plugin;
        this.shopConfig = shopConfig;
        this.promotionsConfig = promotionsConfig;
        this.manager = manager;
        this.service = service;
        this.promotionManager = promotionManager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mysteriacraft.boutique.admin")) {
                messages.send(sender, "general.pas-de-permission");
                return true;
            }
            shopConfig.reload();
            promotionsConfig.reload();
            manager.loadCategories();
            promotionManager.loadCodes();
            messages.send(sender, "boutique.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length > 1 && args[0].equalsIgnoreCase("code")) {
            service.activerCode(player, args[1]);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("jetons")) {
            service.afficherJetons(player);
            return true;
        }

        if (args.length > 2 && args[0].equalsIgnoreCase("convertir")) {
            try {
                if (args[1].equalsIgnoreCase("argent")) {
                    service.convertirArgentEnJetons(player, Double.parseDouble(args[2]));
                } else if (args[1].equalsIgnoreCase("jetons")) {
                    service.convertirJetonsEnArgent(player, Long.parseLong(args[2]));
                } else {
                    messages.send(player, "boutique.convertir-usage");
                }
            } catch (NumberFormatException e) {
                messages.send(player, "boutique.convertir-usage");
            }
            return true;
        }

        new ShopCategoriesGui(plugin, player, manager, service, economyManager, promotionManager, messages).open();
        return true;
    }
}
