package com.mysteriacraft.voucher;

import org.bukkit.Material;

import java.util.List;

/** Definition immuable d'un type de Voucher, chargee depuis voucher.yml. */
public record VoucherDefinition(
        String id,
        String nom,
        Material materiau,
        List<String> lore,
        String commande
) {

    public boolean hasCommande() {
        return commande != null && !commande.isBlank();
    }
}
