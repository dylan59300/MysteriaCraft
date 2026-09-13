package com.mysteriacraft.kits;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.List;

/**
 * Definition immuable d'un kit, chargee depuis kits.yml.
 *
 * @param cooldownOverrides permission -> cooldown (secondes) qui remplace cooldownSeconds
 *                          si le joueur possede la permission (ex: rang VIP = cooldown reduit).
 *                          Si plusieurs permissions correspondent, la plus courte est retenue.
 */
public record Kit(
        String id,
        String displayName,
        Material icon,
        int order,
        List<String> lore,
        String permission,
        long cooldownSeconds,
        Map<String, Long> cooldownOverrides,
        List<ItemStack> items
) {

    public boolean hasPermissionRequirement() {
        return permission != null && !permission.isBlank();
    }
}
