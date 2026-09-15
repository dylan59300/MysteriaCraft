package com.mysteriacraft.encheres;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Une annonce active de l'Hotel des Ventes : un item mis en vente par un joueur a un prix fixe.
 */
public record Annonce(int id, UUID vendeurUuid, String vendeurNom, ItemStack item, double prix, String dateIso) {
}
