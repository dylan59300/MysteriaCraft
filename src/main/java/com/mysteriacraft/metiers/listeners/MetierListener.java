package com.mysteriacraft.metiers.listeners;

import com.mysteriacraft.metiers.MetierService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Hooks de gain d'xp/bonus des metiers Pecheur (peche vanilla) et Alchimiste (consommation de
 * potions). Le Forgeron est recompense directement par EtabliService. */
public class MetierListener implements Listener {

    private final Plugin plugin;
    private final MetierService service;

    public MetierListener(Plugin plugin, MetierService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> service.onCatchFish(player));
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.POTION) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack drunk = event.getItem().clone();
        drunk.setAmount(1);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean refund = service.onDrinkPotion(player);
            if (refund) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.getInventory().addItem(drunk);
                    }
                });
            }
        });
    }
}
