package com.mysteriacraft.voucher.listeners;

import com.mysteriacraft.voucher.VoucherEditorService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

/** Capture la ligne de chat tapee par un admin apres avoir clique sur "+ Creer" ou un champ
 * modifiable dans l'editeur de Vouchers (voir VoucherEditorService). */
public class VoucherEditorChatListener implements Listener {

    private final Plugin plugin;
    private final VoucherEditorService editorService;

    public VoucherEditorChatListener(Plugin plugin, VoucherEditorService editorService) {
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
