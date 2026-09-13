package com.mysteriacraft.crates;

import org.bukkit.ChatColor;

/**
 * Rarete visuelle d'une recompense de caisse (couleur affichee dans le nom/lore).
 */
public enum Rarity {
    COMMUNE(ChatColor.GRAY),
    RARE(ChatColor.BLUE),
    EPIQUE(ChatColor.DARK_PURPLE),
    LEGENDAIRE(ChatColor.GOLD);

    private final ChatColor color;

    Rarity(ChatColor color) {
        this.color = color;
    }

    public ChatColor color() {
        return color;
    }

    public static Rarity fromString(String value) {
        if (value == null) {
            return COMMUNE;
        }
        try {
            return Rarity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMUNE;
        }
    }
}
