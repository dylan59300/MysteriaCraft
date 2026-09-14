package com.mysteriacraft.luckyblock;

import org.bukkit.Material;

import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;

/**
 * Une famille de Lucky Block (ex: commune, rare), chargee depuis luckyblocks.yml.
 * Chaque famille a son propre pool d'effets (aucun cooldown : se recasse immediatement).
 *
 * @param baseGoodChance chance de base (en %) d'obtenir un effet BON plutot que MAUVAIS,
 *                       avant tout bonus de minerai place a cote du bloc (50% par defaut).
 * @param actifDu date de debut (format "MM-jj", ex: "10-20") d'une famille SAISONNIERE (Halloween,
 *                Noel...), ou null pour une famille permanente. Doit etre defini AVEC actifAu.
 * @param actifAu date de fin (format "MM-jj") d'une famille saisonniere, ou null.
 */
public record LuckyBlockFamily(
        String id,
        String displayName,
        Material blockMaterial,
        int order,
        double buyPrice,
        double baseGoodChance,
        List<RecipeIngredient> recipe,
        List<LuckyBlockEffect> effects,
        String actifDu,
        String actifAu
) {

    public boolean isPurchasable() {
        return buyPrice > 0;
    }

    public boolean isSaisonniere() {
        return actifDu != null && actifAu != null;
    }

    /** True si cette famille n'est pas saisonniere, ou si la date du jour tombe dans sa periode
     * d'activite (actifDu -> actifAu, gere le passage du nouvel an, ex: "12-15" -> "01-05"). */
    public boolean isActiveNow() {
        if (!isSaisonniere()) {
            return true;
        }
        MonthDay now = MonthDay.now();
        MonthDay from = parseMonthDay(actifDu);
        MonthDay until = parseMonthDay(actifAu);
        if (from == null || until == null) {
            return true;
        }
        if (from.compareTo(until) <= 0) {
            return !now.isBefore(from) && !now.isAfter(until);
        }
        return !now.isBefore(from) || !now.isAfter(until);
    }

    private static MonthDay parseMonthDay(String raw) {
        try {
            String[] parts = raw.split("-");
            return MonthDay.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            return null;
        }
    }

    public List<LuckyBlockEffect> goodEffects() {
        List<LuckyBlockEffect> good = new ArrayList<>();
        for (LuckyBlockEffect effect : effects) {
            if (effect.kind() == EffectKind.BON) {
                good.add(effect);
            }
        }
        return good;
    }

    public List<LuckyBlockEffect> badEffects() {
        List<LuckyBlockEffect> bad = new ArrayList<>();
        for (LuckyBlockEffect effect : effects) {
            if (effect.kind() == EffectKind.MAUVAIS) {
                bad.add(effect);
            }
        }
        return bad;
    }
}
