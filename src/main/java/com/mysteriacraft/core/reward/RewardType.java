package com.mysteriacraft.core.reward;

/**
 * Nature d'une recompense, partagee entre les modules Crates, BattlePass et Quetes.
 */
public enum RewardType {
    /** Objet donne a l'inventaire. */
    ITEM,
    /** Credit de monnaie interne. */
    ECONOMIE,
    /** Cle(s) virtuelle(s) pour une caisse precise (module Crates). */
    CLE_CAISSE,
    /** Multiplicateur d'xp BattlePass temporaire. */
    BOOST_XP
}
