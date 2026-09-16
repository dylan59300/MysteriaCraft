package com.mysteriacraft.runes;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Grave une rune sur l'arme tenue en main principale (rune tenue en main secondaire), et
 * declenche son effet au moment de toucher un adversaire (voir RuneListener). */
public class RuneService {

    private final RuneManager manager;
    private final CustomItemManager customItemManager;
    private final MessageManager messages;

    public RuneService(RuneManager manager, CustomItemManager customItemManager, MessageManager messages) {
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.messages = messages;
    }

    public void graver(Player player) {
        ItemStack arme = player.getInventory().getItemInMainHand();
        ItemStack runeItem = player.getInventory().getItemInOffHand();

        if (arme.getType().isAir()) {
            messages.send(player, "rune.arme-requise");
            return;
        }
        String runeItemId = customItemManager.getCustomItemId(runeItem);
        RuneManager.RuneType type = manager.getTypeFromItemId(runeItemId);
        if (type == null) {
            messages.send(player, "rune.rune-requise-main-secondaire");
            return;
        }

        ItemMeta meta = arme.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(manager.getRuneKey(), PersistentDataType.STRING, type.id());

        List<String> lore = new ArrayList<>(meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of());
        lore.removeIf(line -> line.contains("Rune :"));
        lore.add(MessageManager.color("&5&lRune : &r" + type.nom()));
        meta.setLore(lore);
        arme.setItemMeta(meta);

        int remaining = runeItem.getAmount() - 1;
        player.getInventory().setItemInOffHand(remaining > 0 ? withAmount(runeItem, remaining) : null);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("rune", type.nom());
        messages.send(player, "rune.gravee", placeholders);
    }

    /** Appele a chaque coup porte par un joueur avec une arme gravee : tire la chance et applique
     * l'effet a la cible (ou au porteur pour VIE). */
    public void handleHit(Player attacker, ItemStack weapon, LivingEntity target) {
        String runeId = weapon.hasItemMeta()
                ? weapon.getItemMeta().getPersistentDataContainer().get(manager.getRuneKey(), PersistentDataType.STRING)
                : null;
        RuneManager.RuneType type = manager.getType(runeId);
        if (type == null) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble(100.0) >= type.chancePourcent()) {
            return;
        }

        switch (type.effet()) {
            case FEU -> target.setFireTicks((int) type.valeur());
            case GLACE -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,
                    (int) type.valeur(), 1, false, true));
            case VIE -> {
                AttributeInstance maxHealthAttribute = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double max = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 20.0;
                attacker.setHealth(Math.min(max, attacker.getHealth() + type.valeur()));
            }
        }
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
