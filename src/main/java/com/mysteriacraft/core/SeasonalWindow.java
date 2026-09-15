package com.mysteriacraft.core;

import java.time.MonthDay;

/**
 * Petit utilitaire partage pour toute config "saisonniere" (Lucky Block, Quete...) activee entre
 * deux dates recurrentes chaque annee (format "MM-jj", ex: "10-20"). Gere le passage du nouvel an
 * (ex: "12-15" -> "01-05"). Les deux dates doivent etre presentes ou absentes ensemble : absentes
 * des deux cotes = pas de fenetre saisonniere (toujours actif).
 */
public final class SeasonalWindow {

    private SeasonalWindow() {
    }

    /** True si aucune fenetre n'est definie (toujours actif), ou si la date du jour y tombe. */
    public static boolean isActiveNow(String from, String until) {
        if (from == null || until == null) {
            return true;
        }
        MonthDay fromDay = parse(from);
        MonthDay untilDay = parse(until);
        if (fromDay == null || untilDay == null) {
            return true;
        }
        MonthDay now = MonthDay.now();
        if (fromDay.compareTo(untilDay) <= 0) {
            return !now.isBefore(fromDay) && !now.isAfter(untilDay);
        }
        // Plage a cheval sur le nouvel an (ex: "12-15" -> "01-05").
        return !now.isBefore(fromDay) || !now.isAfter(untilDay);
    }

    private static MonthDay parse(String raw) {
        try {
            String[] parts = raw.split("-");
            return MonthDay.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            return null;
        }
    }
}
