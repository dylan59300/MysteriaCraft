package com.mysteriacraft.pets.dressage.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.pets.dressage.PetTrainingManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/** /dressage : affiche le niveau de dressage actuel et ses bonus (degats/esquive). */
public class DressageCommand implements CommandExecutor {

    private final Plugin plugin;
    private final PetTrainingManager manager;
    private final MessageManager messages;

    public DressageCommand(Plugin plugin, PetTrainingManager manager, MessageManager messages) {
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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int niveau = manager.getNiveau(player.getUniqueId());
            double bonusDegats = manager.getBonusDegats(player.getUniqueId());
            double bonusEsquive = manager.getBonusEsquive(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("niveau", String.valueOf(niveau));
                placeholders.put("degats", String.valueOf(bonusDegats));
                placeholders.put("esquive", String.valueOf(bonusEsquive));
                messages.send(player, "dressage.info", placeholders);
            });
        });
        return true;
    }
}
