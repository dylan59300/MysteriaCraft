package com.mysteriacraft.shop.listeners;

import com.mysteriacraft.shop.ShopEditorService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

/**
 * Capture la ligne de chat tapee par un admin apres avoir clique sur "+ Ajouter une categorie" ou
 * un champ modifiable dans l'editeur de categories/articles de la Boutique (voir ShopEditorService).
 */
public class ShopEditorChatListener implements Listener {

    private final Plugin plugin;
    private final ShopEditorService editorService;

    public ShopEditorChatListener(Plugin plugin, ShopEditorService editorService) {
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
