package com.mysteriacraft.battlepass.listeners;

import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Prefixe le message de chat du titre actif du joueur (voir RewardType.TITRE_CHAT et
 * /battlepass titre), s'il en a selectionne un.
 */
public class ChatTitleListener implements Listener {

    private final BattlePassService battlePassService;

    public ChatTitleListener(BattlePassService battlePassService) {
        this.battlePassService = battlePassService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String titre = battlePassService.getActiveTitle(event.getPlayer().getUniqueId());
        if (titre != null) {
            event.setFormat(MessageManager.color("&7[" + titre + "&7] ") + event.getFormat());
        }
    }
}
