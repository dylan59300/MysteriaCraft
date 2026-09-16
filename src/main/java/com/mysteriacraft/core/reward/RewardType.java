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
    /** Donne un Generateur de Lucky Block cible sur une famille precise (module LuckyBlock). */
    GENERATEUR_LUCKYBLOCK,
    /** Donne une Machine ("transformation" ou "miniere") a son tier de base (module Custom Items). */
    MACHINE,
    /** Agrandit gratuitement le rayon protege de l'ile du joueur, d'un nombre de blocs fixe
     * (module Iles), plafonne a taille-max comme /ile upgrade. */
    AGRANDISSEMENT_ILE,
    /** Debloque un titre de chat (module BattlePass), selectionnable ensuite via /battlepass titre. */
    TITRE_CHAT
}
