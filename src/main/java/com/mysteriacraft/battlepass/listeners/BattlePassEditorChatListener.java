package com.mysteriacraft.battlepass.listeners;

import com.mysteriacraft.battlepass.BattlePassEditorService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

/**
 * Capture la ligne de chat tapee par un admin apres avoir clique sur "Modifier l'xp requise"
 * dans l'editeur de paliers en jeu (voir BattlePassEditorService#requestXpInput).
 */
public class BattlePassEditorChatListener implements Listener {

    private final Plugin plugin;
    private final BattlePassEditorService editorService;

    public BattlePassEditorChatListener(Plugin plugin, BattlePassEditorService editorService) {
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
