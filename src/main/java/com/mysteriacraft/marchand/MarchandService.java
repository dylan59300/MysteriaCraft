package com.mysteriacraft.marchand;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.customitems.CustomItemManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestre le PNJ Marchand : invocation (villageois immobile/invulnerable marque via
 * PersistentDataContainer, persiste automatiquement avec le monde comme toute entite), et
 * echange d'une offre contre des pieces d'echange (item custom, retirees de l'inventaire du
 * joueur, potentiellement reparties sur plusieurs piles).
 */
public class MarchandService {

    private final MarchandManager manager;
    private final CustomItemManager customItemManager;
    private final RewardGiver rewardGiver;
    private final MessageManager messages;
    private final NamespacedKey npcKey;

    public MarchandService(Plugin plugin, MarchandManager manager, CustomItemManager customItemManager,
                            RewardGiver rewardGiver, MessageManager messages) {
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.rewardGiver = rewardGiver;
        this.messages = messages;
        this.npcKey = new NamespacedKey(plugin, "npc-marchand");
    }

    /** Invoque le PNJ Marchand a cet emplacement : immobile, invulnerable, ne peut ni se
     * reproduire ni se transformer (zombifier), et persiste au redemarrage comme toute entite. */
    public void spawnNpc(Location location) {
        Villager villager = location.getWorld().spawn(location, Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(false);
            entity.setPersistent(true);
            entity.setCanPickupItems(false);
            entity.setCustomName(MessageManager.color(manager.getNpcName()));
            entity.setCustomNameVisible(true);
            entity.setProfession(Villager.Profession.NONE);
            entity.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        });
        villager.setRemoveWhenFarAway(false);
    }

    public boolean isMarchand(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    /** Nombre total de pieces d'echange possedees par ce joueur (toutes piles confondues). */
    public int countPieces(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && manager.getPieceItemId().equalsIgnoreCase(customItemManager.getCustomItemId(item))) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void removePieces(Player player, int amount) {
        int remaining = amount;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || !manager.getPieceItemId().equalsIgnoreCase(customItemManager.getCustomItemId(item))) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            if (take >= item.getAmount()) {
                inventory.setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - take);
            }
            remaining -= take;
        }
    }

    /** Echange une offre : verifie le nombre de pieces d'echange, les retire, puis donne la
     * recompense via RewardGiver (comme les autres modules). */
    public void purchase(Player player, MarchandOffer offer) {
        if (offer.recompense() == null) {
            messages.send(player, "marchand.offre-invalide");
            return;
        }
        if (countPieces(player) < offer.cout()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("cout", String.valueOf(offer.cout()));
            messages.send(player, "marchand.pieces-insuffisantes", placeholders);
            return;
        }
        removePieces(player, offer.cout());
        rewardGiver.give(player, offer.recompense());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("recompense", offer.displayName());
        placeholders.put("cout", String.valueOf(offer.cout()));
        messages.send(player, "marchand.echange-reussi", placeholders);
    }
}
