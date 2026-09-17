package com.mysteriacraft.battlepass;

import com.mysteriacraft.battlepass.gui.BattlePassLevelEditorGui;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gere la saisie au clavier (via le chat) utilisee par l'editeur de paliers en jeu (voir
 * BattlePassEditorGui/BattlePassLevelEditorGui et /battlepassadmin editeur), pour les champs
 * numeriques qu'un simple clic ne peut pas raisonnablement couvrir (l'xp requise d'un palier).
 */
public class BattlePassEditorService {

    private final Plugin plugin;
    private final BattlePassManager manager;
    private final MessageManager messages;

    /** Joueur en attente de taper une valeur dans le chat, associe au palier concerne. */
    private final Map<UUID, Integer> pendingXpInput = new ConcurrentHashMap<>();

    public BattlePassEditorService(Plugin plugin, BattlePassManager manager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    /** Ferme le menu, previent le joueur, et attend sa prochaine ligne de chat comme nouvel xp-requis. */
    public void requestXpInput(Player player, int level) {
        pendingXpInput.put(player.getUniqueId(), level);
        player.closeInventory();
        messages.send(player, "battlepass.editeur-saisir-xp");
    }

    public boolean hasPendingInput(UUID uuid) {
        return pendingXpInput.containsKey(uuid);
    }

    /** Appele par BattlePassEditorChatListener (deja sur le thread principal) avec le message tape. */
    public void handleChatInput(Player player, String message) {
        Integer level = pendingXpInput.remove(player.getUniqueId());
        if (level == null) {
            return;
        }
        long xp;
        try {
            xp = Long.parseLong(message.trim());
        } catch (NumberFormatException e) {
            messages.send(player, "battlepass.editeur-xp-invalide");
            reopenLevelEditor(player, level);
            return;
        }
        manager.setLevelXpRequired(level, xp);
        messages.send(player, "battlepass.editeur-xp-modifiee");
        reopenLevelEditor(player, level);
    }

    private void reopenLevelEditor(Player player, int level) {
        Bukkit.getScheduler().runTask(plugin, () -> new BattlePassLevelEditorGui(plugin, player, manager, this, messages, level).open());
    }
}
