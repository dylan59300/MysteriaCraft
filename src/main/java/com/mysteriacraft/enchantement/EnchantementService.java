package com.mysteriacraft.enchantement;

import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applique un enchantement aleatoire (pondere) du pool a un item, contre des niveaux d'XP
 * (deja verifies/retires par l'appelant, voir EnchantementListener).
 */
public class EnchantementService {

    private final EnchantementManager manager;
    private final MessageManager messages;

    public EnchantementService(EnchantementManager manager, MessageManager messages) {
        this.manager = manager;
        this.messages = messages;
    }

    /** Tire un enchantement au hasard (pondere) dans le pool, ou null si le pool est vide. */
    private EnchantementPool rollEnchantement() {
        var pool = manager.getPool();
        if (pool.isEmpty()) {
            return null;
        }
        int totalPoids = pool.stream().mapToInt(EnchantementPool::poids).sum();
        int roll = ThreadLocalRandom.current().nextInt(totalPoids);
        int cumul = 0;
        for (EnchantementPool entry : pool) {
            cumul += entry.poids();
            if (roll < cumul) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** Applique un enchantement tire au hasard sur cet item (EN PLACE), et renvoie l'enchantement
     * applique (nom + niveau, pour le message), ou null si le pool est vide. */
    public EnchantementPool enchant(Player player, ItemStack item) {
        EnchantementPool rolled = rollEnchantement();
        if (rolled == null) {
            messages.send(player, "enchantement.pool-vide");
            return null;
        }
        int niveau = rolled.niveauMin() == rolled.niveauMax() ? rolled.niveauMin()
                : ThreadLocalRandom.current().nextInt(rolled.niveauMin(), rolled.niveauMax() + 1);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(rolled.enchantement(), niveau, true);
            item.setItemMeta(meta);
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("enchantement", rolled.enchantement().getKey().getKey());
        placeholders.put("niveau", String.valueOf(niveau));
        messages.send(player, "enchantement.reussi", placeholders);
        return rolled;
    }
}
