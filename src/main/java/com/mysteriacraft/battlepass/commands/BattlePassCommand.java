package com.mysteriacraft.battlepass.commands;

import com.mysteriacraft.battlepass.BattlePassManager;
import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.battlepass.gui.BattlePassGui;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class BattlePassCommand implements CommandExecutor {

    private final Plugin plugin;
    private final BattlePassManager manager;
    private final BattlePassService service;
    private final MessageManager messages;

    public BattlePassCommand(Plugin plugin, BattlePassManager manager, BattlePassService service, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long xp = manager.getXp(player.getUniqueId());
            boolean premium = manager.isPremium(player.getUniqueId());
            var claims = manager.getAllClaims(player.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () ->
                    new BattlePassGui(plugin, player, manager, service, messages, xp, premium, claims).open());
        });
        return true;
    }
}
