package com.mysteriacraft.crates;

/**
 * Style d'animation joue a l'ouverture d'une caisse.
 */
public enum CrateAnimationType {
    /** Donne la recompense immediatement, avec juste un ecran de confirmation. */
    INSTANT,
    /** Bandeau d'objets qui defile horizontalement puis ralentit pour s'arreter sur le gain (style "case opening"). */
    ROULETTE,
    /** L'icone centrale change rapidement puis se fixe sur le gain (reveal rapide). */
    QUICK_REVEAL;

    public static CrateAnimationType fromString(String value) {
        if (value == null) {
            return INSTANT;
        }
        try {
            return CrateAnimationType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return INSTANT;
        }
    }
}
