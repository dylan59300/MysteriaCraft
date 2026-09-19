package com.mysteriacraft.admin.listeners;

import com.mysteriacraft.admin.AdminMuteManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Coupe le chat d'un joueur mute (voir AdminMuteManager, sanctions rapides du panel admin).
 */
public class AdminMuteChatListener implements Listener {

    private final AdminMuteManager muteManager;
    private final MessageManager messages;

    public AdminMuteChatListener(AdminMuteManager muteManager, MessageManager messages) {
        this.muteManager = muteManager;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        AdminMuteManager.MuteInfo mute = muteManager.getMute(event.getPlayer().getUniqueId());
        if (mute == null) {
            return;
        }
        event.setCancelled(true);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("raison", mute.raison());
        placeholders.put("expiration", mute.expireAtMillis() == AdminMuteManager.PERMANENT
                ? messages.raw("admin.mute-permanent") : String.valueOf((mute.expireAtMillis() - System.currentTimeMillis()) / 60_000L));
        messages.send(event.getPlayer(), "admin.mute-actif", placeholders);
    }
}
