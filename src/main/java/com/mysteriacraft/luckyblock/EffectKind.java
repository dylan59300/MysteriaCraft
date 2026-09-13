package com.mysteriacraft.luckyblock;

public enum EffectKind {
    BON,
    MAUVAIS;

    public static EffectKind fromString(String value) {
        try {
            return EffectKind.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return BON;
        }
    }
}
