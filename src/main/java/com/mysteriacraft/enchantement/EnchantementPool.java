package com.mysteriacraft.enchantement;

import org.bukkit.enchantments.Enchantment;

/**
 * Une entree du pool de la Table d'Enchantement Custom : un enchantement, un intervalle de
 * niveau (tire uniformement) et un poids relatif de tirage.
 */
public record EnchantementPool(Enchantment enchantement, int niveauMin, int niveauMax, int poids) {
}
