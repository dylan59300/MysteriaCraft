package com.mysteriacraft.kits;

import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestre la reclamation d'un kit : permission, cooldown (SQLite, hors thread principal)
 * puis distribution des objets (thread principal, API Bukkit oblige).
 * Partagee entre la commande /kit et le menu GUI pour eviter de dupliquer la logique.
 */
public class KitService {

    private final Plugin plugin;
    private final KitManager kitManager;
    private final MessageManager messages;

    public KitService(Plugin plugin, KitManager kitManager, MessageManager messages) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.messages = messages;
    }

    public void claim(Player player, String kitId) {
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            messages.send(player, "kits.introuvable");
            return;
        }

        if (kit.hasPermissionRequirement() && !player.hasPermission(kit.permission())) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("kit", kit.displayName());
            messages.send(player, "kits.pas-permission", placeholders);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long lastUsed = kitManager.getLastUsed(player.getUniqueId(), kit.id());
            long remainingMillis = (lastUsed + kit.cooldownSeconds() * 1000L) - System.currentTimeMillis();

            if (lastUsed > 0 && remainingMillis > 0) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("kit", kit.displayName());
                    placeholders.put("temps", formatDuration(remainingMillis));
                    messages.send(player, "kits.cooldown", placeholders);
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> giveKit(player, kit));
        });
    }

    private void giveKit(Player player, Kit kit) {
        List<ItemStack> items = kitManager.cloneItems(kit);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(new ItemStack[0]));

        if (!leftovers.isEmpty()) {
            Location dropLocation = player.getLocation();
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(dropLocation, leftover);
            }
            messages.send(player, "kits.inventaire-plein");
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> kitManager.markUsed(player.getUniqueId(), kit.id()));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("kit", kit.displayName());
        messages.send(player, "kits.recu", placeholders);
    }

    /** Formate une duree en millisecondes en "XhYmZs" (n'affiche que les unites non nulles). */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
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
