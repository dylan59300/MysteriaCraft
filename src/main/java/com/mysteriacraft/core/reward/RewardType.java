package com.mysteriacraft.core.reward;

/**
 * Nature d'une recompense, partagee entre les modules BattlePass et Quetes.
 */
public enum RewardType {
    /** Objet donne a l'inventaire. */
    ITEM,
    /** Credit de monnaie interne. */
    ECONOMIE,
    /** Multiplicateur d'xp BattlePass temporaire. */
    BOOST_XP,
    /** Debloque un pet (module Pets). */
    PET,
    /** Donne un item Lucky Block marque (module LuckyBlock). */
    LUCKYBLOCK,
    /** Donne un minerai/objet custom (module Custom Items). */
    OBJET_CUSTOM,
    /** Donne un Generateur d'Argent d'un type precis (module Generateurs). */
    GENERATEUR,
    /** Donne une Machine ("transformation" ou "miniere") a son tier de base (module Custom Items). */
    MACHINE
}
