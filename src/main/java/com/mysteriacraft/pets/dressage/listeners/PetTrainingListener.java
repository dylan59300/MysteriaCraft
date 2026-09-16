package com.mysteriacraft.pets.dressage.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.pets.PetService;
import com.mysteriacraft.pets.dressage.PetTrainingManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Chaque kill d'un joueur AVEC un pet actif donne de l'xp de dressage a ce pet (voir
 * PetTrainingManager). */
public class PetTrainingListener implements Listener {

    private final Plugin plugin;
    private final PetTrainingManager trainingManager;
    private final PetService petService;
    private final MessageManager messages;

    public PetTrainingListener(Plugin plugin, PetTrainingManager trainingManager, PetService petService, MessageManager messages) {
        this.plugin = plugin;
        this.trainingManager = trainingManager;
        this.petService = petService;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player player)) {
            return;
        }
        if (!petService.hasActivePet(player.getUniqueId())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long xpParKill = trainingManager.getXpParKill();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean leveledUp = trainingManager.addXp(uuid, xpParKill);
            if (leveledUp) {
                int niveau = trainingManager.getNiveau(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    petService.refreshTrainingBonus(player);
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("niveau", String.valueOf(niveau));
                    messages.send(player, "dressage.niveau-suivant", placeholders);
                });
            }
        });
    }
}
