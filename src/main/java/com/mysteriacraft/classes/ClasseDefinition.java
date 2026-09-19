package com.mysteriacraft.classes;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Definition immuable d'une classe/metier (bonus passif applique via un effet de potion
 * permanent tant que cette classe est active), chargee depuis classes.yml.
 */
public record ClasseDefinition(
        String id,
        String displayName,
        Material icon,
        List<String> lore,
        PotionEffectType effet,
        int amplificateur
) {
}
