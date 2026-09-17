package com.mysteriacraft.luckyblock.listeners;

import com.mysteriacraft.luckyblock.LuckyBlockEditorService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

/**
 * Capture la ligne de chat tapee par un admin dans l'editeur de familles/effets de Lucky Block en
 * jeu (voir LuckyBlockEditorService).
 */
public class LuckyBlockEditorChatListener implements Listener {

    private final Plugin plugin;
    private final LuckyBlockEditorService editorService;

    public LuckyBlockEditorChatListener(Plugin plugin, LuckyBlockEditorService editorService) {
        this.plugin = plugin;
        this.editorService = editorService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!editorService.hasPendingInput(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> editorService.handleChatInput(event.getPlayer(), message));
    }
}
