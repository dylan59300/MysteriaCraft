package com.mysteriacraft.economy;

import java.util.UUID;

/**
 * Represente un paiement en attente de confirmation (montant superieur au seuil configure).
 */
public record PendingPayment(UUID targetUuid, String targetName, double amount, long expiresAtMillis) {

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAtMillis;
    }
}
