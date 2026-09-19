package com.mysteriacraft.resourcepack;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/** Propose le resource pack officiel a chaque connexion (voir resourcepack.yml). */
public class ResourcePackListener implements Listener {

    private final Plugin plugin;
    private final ResourcePackManager manager;

    public ResourcePackListener(Plugin plugin, ResourcePackManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!manager.isEnvoiAutomatique() || !manager.isConfigure()) {
            return;
        }
        Player player = event.getPlayer();
        // Petit delai pour laisser le client finir de charger le monde avant de lui proposer le pack.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                envoyer(player);
            }
        }, 40L);
    }

    @SuppressWarnings("deprecation")
    public void envoyer(Player player) {
        player.setResourcePack(manager.getUrl());
    }
}
