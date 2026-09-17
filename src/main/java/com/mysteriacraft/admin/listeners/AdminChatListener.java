package com.mysteriacraft.admin.listeners;

import com.mysteriacraft.admin.AdminChatService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

/**
 * Capture la ligne de chat tapee par un admin apres "Broadcast rapide" dans le panel admin (voir
 * AdminChatService).
 */
public class AdminChatListener implements Listener {

    private final Plugin plugin;
    private final AdminChatService chatService;

    public AdminChatListener(Plugin plugin, AdminChatService chatService) {
        this.plugin = plugin;
        this.chatService = chatService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!chatService.hasPendingInput(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> chatService.handleChatInput(event.getPlayer(), message));
    }
}
