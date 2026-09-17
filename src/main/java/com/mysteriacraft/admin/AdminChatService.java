package com.mysteriacraft.admin;

import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saisie au clavier utilisee par le panel admin (voir /admin, bouton "Broadcast rapide") : le
 * message tape par l'admin dans le chat est diffuse a tout le serveur. Sur le meme principe que
 * BattlePassEditorService/ShopEditorService.
 */
public class AdminChatService {

    private final MessageManager messages;
    private final Set<UUID> enAttenteBroadcast = ConcurrentHashMap.newKeySet();

    public AdminChatService(MessageManager messages) {
        this.messages = messages;
    }

    public void requestBroadcast(Player player) {
        enAttenteBroadcast.add(player.getUniqueId());
        player.closeInventory();
        messages.send(player, "admin.broadcast-saisir");
    }

    public boolean hasPendingInput(UUID uuid) {
        return enAttenteBroadcast.contains(uuid);
    }

    public void handleChatInput(Player player, String message) {
        if (!enAttenteBroadcast.remove(player.getUniqueId())) {
            return;
        }
        String texte = replace(messages.raw("admin.broadcast-format"), message);
        Bukkit.broadcastMessage(MessageManager.color(texte));
    }

    private String replace(String format, String message) {
        return format.replace("{message}", message);
    }
}
