package com.mysteriacraft.economy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stocke les paiements en attente de confirmation (un seul par payeur a la fois).
 */
public class PendingPaymentManager {

    private final Map<UUID, PendingPayment> pending = new ConcurrentHashMap<>();

    public void set(UUID payer, PendingPayment payment) {
        pending.put(payer, payment);
    }

    /** Recupere et retire le paiement en attente d'un joueur (null si absent ou expire). */
    public PendingPayment consume(UUID payer) {
        PendingPayment payment = pending.remove(payer);
        if (payment == null || payment.isExpired()) {
            return null;
        }
        return payment;
    }

    public void clear(UUID payer) {
        pending.remove(payer);
    }
}
