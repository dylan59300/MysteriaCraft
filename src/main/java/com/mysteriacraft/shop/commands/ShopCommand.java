package com.mysteriacraft.shop.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.shop.ShopManager;
import com.mysteriacraft.shop.ShopService;
import com.mysteriacraft.shop.gui.ShopCategoriesGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /boutique : ouvre le menu de la Boutique. /boutique reload : recharge boutique.yml (admin). */
public class ShopCommand implements CommandExecutor {

    private final ConfigManager shopConfig;
    private final ShopManager manager;
    private final ShopService service;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public ShopCommand(ConfigManager shopConfig, ShopManager manager, ShopService service,
                        EconomyManager economyManager, MessageManager messages) {
        this.shopConfig = shopConfig;
        this.manager = manager;
        this.service = service;
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
            manager.loadCategories();
            messages.send(sender, "boutique.reload");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        new ShopCategoriesGui(player, manager, service, economyManager, messages).open();
        return true;
    }
}
