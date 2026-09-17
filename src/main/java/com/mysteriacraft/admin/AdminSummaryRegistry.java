package com.mysteriacraft.admin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Resumes optionnels affiches dans la lore d'un module du panel admin (voir AdminPanelGui), du
 * genre "12 categories" ou "48 paliers". Rempli une fois au demarrage (voir MysteriaCraft#setupAdminPanel)
 * avec les managers deja instancies ; un module sans resume enregistre garde une lore generique.
 */
public final class AdminSummaryRegistry {

    private static final Map<String, Supplier<String>> SUMMARIES = new HashMap<>();

    private AdminSummaryRegistry() {
    }

    public static void register(String label, Supplier<String> resume) {
        SUMMARIES.put(label, resume);
    }

    /** null si aucun resume enregistre pour ce module. */
    public static String get(String label) {
        Supplier<String> supplier = SUMMARIES.get(label);
        if (supplier == null) {
            return null;
        }
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }
}
