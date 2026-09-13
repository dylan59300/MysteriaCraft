package com.mysteriacraft.luckyblock;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Une famille de Lucky Block (ex: commune, rare), chargee depuis luckyblocks.yml.
 * Chaque famille a son propre pool d'effets et son propre cooldown.
 *
 * @param baseGoodChance chance de base (en %) d'obtenir un effet BON plutot que MAUVAIS,
 *                       avant tout bonus de minerai place a cote du bloc (50% par defaut).
 */
public record LuckyBlockFamily(
        String id,
        String displayName,
        Material blockMaterial,
        int order,
        long cooldownSeconds,
        double buyPrice,
        double baseGoodChance,
        List<RecipeIngredient> recipe,
        List<LuckyBlockEffect> effects
) {

    public boolean isPurchasable() {
        return buyPrice > 0;
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
