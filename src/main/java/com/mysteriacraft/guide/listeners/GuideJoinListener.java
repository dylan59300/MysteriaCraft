package com.mysteriacraft.guide.listeners;

import com.mysteriacraft.guide.GuideGui;
import com.mysteriacraft.guide.GuideManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Ouvre automatiquement le guide (/guide) a la toute premiere connexion d'un joueur (suivi en
 * base via GuideManager, une seule fois par joueur). Un delai de 20 ticks laisse l'ecran de
 * connexion se stabiliser avant d'ouvrir un inventaire.
 */
public class GuideJoinListener implements Listener {

    private final Plugin plugin;
    private final GuideManager manager;
    private final MessageManager messages;

    public GuideJoinListener(Plugin plugin, GuideManager manager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!manager.markSeenAndCheckFirstTime(event.getPlayer().getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                new GuideGui(event.getPlayer(), manager, messages).open();
            }
        }, 20L);
    }
}
