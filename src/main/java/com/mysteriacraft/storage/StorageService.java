package com.mysteriacraft.storage;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Traite l'ouverture et l'amelioration des espaces de stockage personnels : le Sac (ameliore avec
 * un item custom consommable) et le Coffre-fort (ameliore avec de l'argent). Les deux partagent
 * la meme persistance generique (voir PersonalStorageManager), seul le "type" differe.
 */
public class StorageService {

    public static final String TYPE_SAC = "sac";
    public static final String TYPE_COFFREFORT = "coffrefort";

    private final Plugin plugin;
    private final PersonalStorageManager manager;
    private final ConfigManager storageConfig;
    private final CustomItemManager customItemManager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public StorageService(Plugin plugin, PersonalStorageManager manager, ConfigManager storageConfig,
                           CustomItemManager customItemManager, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.storageConfig = storageConfig;
        this.customItemManager = customItemManager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public void open(Player player, String type) {
        UUID uuid = player.getUniqueId();
        int baseSize = storageConfig.get().getInt(type + ".taille-base", 9);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int size = manager.getSize(uuid, type, baseSize);
            ItemStack[] contents = manager.loadContents(uuid, type, size);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                StorageHolder holder = new StorageHolder(uuid, type);
                String titleKey = type.equals(TYPE_SAC) ? "stockage.titre-sac" : "stockage.titre-coffrefort";
                Inventory inventory = Bukkit.createInventory(holder, size, MessageManager.color(messages.raw(titleKey)));
                inventory.setContents(contents);
                holder.setInventory(inventory);
                player.openInventory(inventory);
            });
        });
    }

    /** A appeler par le listener a la fermeture d'un inventaire de stockage : persiste son contenu. */
    public void handleClose(StorageHolder holder) {
        ItemStack[] contents = holder.getInventory().getContents();
        UUID uuid = holder.getUuid();
        String type = holder.getType();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> manager.saveContents(uuid, type, contents.length, contents));
    }

    /** Ameliore le Sac en consommant l'item custom "sac.item-amelioration" tenu en main. */
    public void upgradeSac(Player player) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String requiredItemId = storageConfig.get().getString("sac.item-amelioration", "");
        String heldId = customItemManager.getCustomItemId(inHand);
        if (heldId == null || !heldId.equalsIgnoreCase(requiredItemId)) {
            messages.send(player, "stockage.item-amelioration-requis");
            return;
        }

        UUID uuid = player.getUniqueId();
        int baseSize = storageConfig.get().getInt("sac.taille-base", 9);
        int maxSize = storageConfig.get().getInt("sac.taille-max", 54);
        int slotsParAmelioration = storageConfig.get().getInt("sac.slots-par-amelioration", 9);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int currentSize = manager.getSize(uuid, TYPE_SAC, baseSize);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (currentSize >= maxSize) {
                    messages.send(player, "stockage.deja-max");
                    return;
                }
                int remaining = inHand.getAmount() - 1;
                player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);

                int newSize = Math.min(maxSize, currentSize + slotsParAmelioration);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> manager.setSize(uuid, TYPE_SAC, newSize));

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("taille", String.valueOf(newSize));
                messages.send(player, "stockage.ameliore", placeholders);
            });
        });
    }

    /** Ameliore le Coffre-fort en payant "coffrefort.cout-par-amelioration" en argent. */
    public void upgradeCoffreFort(Player player) {
        UUID uuid = player.getUniqueId();
        int baseSize = storageConfig.get().getInt("coffrefort.taille-base", 9);
        int maxSize = storageConfig.get().getInt("coffrefort.taille-max", 54);
        int slotsParAmelioration = storageConfig.get().getInt("coffrefort.slots-par-amelioration", 9);
        double cout = storageConfig.get().getDouble("coffrefort.cout-par-amelioration", 5000);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int currentSize = manager.getSize(uuid, TYPE_COFFREFORT, baseSize);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (currentSize >= maxSize) {
                    messages.send(player, "stockage.deja-max");
                    return;
                }
                if (!economyManager.withdraw(uuid, cout)) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("cout", economyManager.format(cout));
                    messages.send(player, "stockage.fonds-insuffisants", placeholders);
                    return;
                }
                int newSize = Math.min(maxSize, currentSize + slotsParAmelioration);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> manager.setSize(uuid, TYPE_COFFREFORT, newSize));

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("taille", String.valueOf(newSize));
                messages.send(player, "stockage.ameliore", placeholders);
            });
        });
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
