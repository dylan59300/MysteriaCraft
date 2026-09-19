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
 * numeriques/textuels qu'un simple clic ne peut pas raisonnablement couvrir (l'xp requise d'un
 * palier, ou le texte d'une commande de recompense).
 */
public class BattlePassEditorService {

    private record PendingRewardCommand(int level, String piste) {
    }

    private final Plugin plugin;
    private final BattlePassManager manager;
    private final MessageManager messages;

    /** Joueur en attente de taper une valeur dans le chat, associe au palier concerne. */
    private final Map<UUID, Integer> pendingXpInput = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRewardCommand> pendingRewardCommand = new ConcurrentHashMap<>();

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

    /** Ferme le menu, previent le joueur, et attend sa prochaine ligne de chat comme commande de
     * recompense (voir RewardType.COMMANDE) pour cette piste ("gratuit"/"premium"). */
    public void requestRewardCommand(Player player, int level, String piste) {
        pendingRewardCommand.put(player.getUniqueId(), new PendingRewardCommand(level, piste));
        player.closeInventory();
        messages.send(player, "battlepass.editeur-saisir-recompense-commande");
    }

    public boolean hasPendingInput(UUID uuid) {
        return pendingXpInput.containsKey(uuid) || pendingRewardCommand.containsKey(uuid);
    }

    /** Appele par BattlePassEditorChatListener (deja sur le thread principal) avec le message tape. */
    public void handleChatInput(Player player, String message) {
        Integer level = pendingXpInput.remove(player.getUniqueId());
        if (level != null) {
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
            return;
        }

        PendingRewardCommand pending = pendingRewardCommand.remove(player.getUniqueId());
        if (pending != null) {
            String commande = message.trim();
            if (commande.isEmpty()) {
                messages.send(player, "battlepass.editeur-valeur-invalide");
            } else {
                manager.setLevelRewardCommand(pending.level(), pending.piste(), commande);
                messages.send(player, "battlepass.editeur-recompense-commande-definie");
            }
            reopenLevelEditor(player, pending.level());
        }
    }

    private void reopenLevelEditor(Player player, int level) {
        Bukkit.getScheduler().runTask(plugin, () -> new BattlePassLevelEditorGui(plugin, player, manager, this, messages, level).open());
    }
}
