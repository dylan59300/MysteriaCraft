package com.mysteriacraft.luckyblock;

/**
 * Type d'effet "mauvais" declenchable a la casse d'un Lucky Block.
 */
public enum BadEffectType {
    TNT,
    MOBS,
    POTION,
    FOUDRE;

    public static BadEffectType fromString(String value) {
        try {
            return BadEffectType.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return TNT;
        }
    }
}
