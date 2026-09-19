package com.mysteriacraft.economy.listeners;

import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class EconomyJoinQuitListener implements Listener {

    private final Plugin plugin;
    private final EconomyManager economyManager;

    public EconomyJoinQuitListener(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> economyManager.loadAccount(player.getUniqueId(), player.getName()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        economyManager.unloadAccount(event.getPlayer().getUniqueId());
    }
}
