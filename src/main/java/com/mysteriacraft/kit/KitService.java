package com.mysteriacraft.kit;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Traite la recuperation d'un kit : verifie la permission et le cooldown (lecture SQLite, donc
 * hors du thread principal) puis distribue ses recompenses via le RewardGiver partage.
 */
public class KitService {

    private final Plugin plugin;
    private final KitManager manager;
    private final RewardGiver rewardGiver;
    private final MessageManager messages;

    public KitService(Plugin plugin, KitManager manager, RewardGiver rewardGiver, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.rewardGiver = rewardGiver;
        this.messages = messages;
    }

    public void claim(Player player, String kitId) {
        KitManager.Kit kit = manager.getKit(kitId);
        if (kit == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("ids", String.join(", ", manager.getKitsSorted().stream().map(KitManager.Kit::id).toList()));
            messages.send(player, "kit.introuvable", placeholders);
            return;
        }
        if (kit.permission() != null && !kit.permission().isBlank() && !player.hasPermission(kit.permission())) {
            messages.send(player, "general.pas-de-permission");
            return;
        }

        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long remaining = manager.getRemainingCooldownMillis(uuid, kit);
            Bukkit.getScheduler().runTask(plugin, () -> applyResult(player, kit, remaining));
        });
    }

    private void applyResult(Player player, KitManager.Kit kit, long remainingMillis) {
        if (!player.isOnline()) {
            return;
        }
        if (remainingMillis < 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("kit", kit.displayName());
            messages.send(player, "kit.deja-recupere", placeholders);
            return;
        }
        if (remainingMillis > 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("kit", kit.displayName());
            placeholders.put("temps", formatDuration(remainingMillis));
            messages.send(player, "kit.cooldown", placeholders);
            return;
        }

        rewardGiver.giveAll(player, kit.rewards());
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> manager.markClaimed(uuid, kit.id()));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("kit", kit.displayName());
        messages.send(player, "kit.recupere", placeholders);
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours).append("h");
        }
        if (minutes > 0) {
            builder.append(minutes).append("m");
        }
        if (hours == 0 && (seconds > 0 || builder.isEmpty())) {
            builder.append(seconds).append("s");
        }
        return builder.toString();
    }
}
