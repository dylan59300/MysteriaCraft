package com.mysteriacraft.gemmes;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Insere une gemme (tenue en main SECONDAIRE) dans l'equipement tenu en main PRINCIPALE : ajoute
 * un modificateur d'attribut permanent sur l'item (remplace une gemme deja inseree, une seule
 * gemme par piece d'equipement). */
public class GemmeService {

    private static final UUID MODIFIER_UUID = UUID.fromString("b7e1a2c3-9f4d-4a2e-8c1b-3d5e7f9a1b2c");

    private final GemmeManager manager;
    private final CustomItemManager customItemManager;
    private final MessageManager messages;

    public GemmeService(GemmeManager manager, CustomItemManager customItemManager, MessageManager messages) {
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.messages = messages;
    }

    public void inserer(Player player) {
        ItemStack equipement = player.getInventory().getItemInMainHand();
        ItemStack gemmeItem = player.getInventory().getItemInOffHand();

        if (equipement.getType().isAir()) {
            messages.send(player, "gemme.equipement-requis");
            return;
        }
        String gemmeItemId = customItemManager.getCustomItemId(gemmeItem);
        GemmeManager.GemmeType type = manager.getTypeFromItemId(gemmeItemId);
        if (type == null) {
            messages.send(player, "gemme.gemme-requise-main-secondaire");
            return;
        }

        EquipmentSlot slot = resolveSlot(equipement.getType());
        if (slot == null) {
            messages.send(player, "gemme.equipement-invalide");
            return;
        }

        ItemMeta meta = equipement.getItemMeta();
        if (meta == null) {
            return;
        }

        if (meta.getAttributeModifiers() != null) {
            meta.getAttributeModifiers().entries().stream()
                    .filter(entry -> entry.getValue().getUniqueId().equals(MODIFIER_UUID))
                    .toList()
                    .forEach(entry -> meta.removeAttributeModifier(entry.getKey(), entry.getValue()));
        }

        meta.addAttributeModifier(type.attribut(), new AttributeModifier(MODIFIER_UUID,
                "mysteriacraft-gemme", type.valeur(), AttributeModifier.Operation.ADD_NUMBER, slot));
        meta.getPersistentDataContainer().set(manager.getGemmeKey(), PersistentDataType.STRING, type.id());

        List<String> lore = new ArrayList<>(meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of());
        lore.removeIf(line -> line.contains("Gemme :"));
        lore.add(MessageManager.color("&d&lGemme : &r" + type.nom()));
        meta.setLore(lore);

        equipement.setItemMeta(meta);

        int remaining = gemmeItem.getAmount() - 1;
        player.getInventory().setItemInOffHand(remaining > 0 ? withAmount(gemmeItem, remaining) : null);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("gemme", type.nom());
        messages.send(player, "gemme.inseree", placeholders);
    }

    /** Emplacement d'equipement d'un materiau (arme/outil -> main, piece d'armure -> sa case),
     * ou null si ce n'est ni l'un ni l'autre. */
    private EquipmentSlot resolveSlot(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            return EquipmentSlot.HAND;
        }
        return null;
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
