package com.mysteriacraft.battlepass;

/**
 * Nature d'une recompense de palier : soit un objet donne a l'inventaire, soit un credit d'economie.
 * Duplique volontairement crates.RewardType pour garder les modules independants.
 */
public enum RewardType {
    ITEM,
    ECONOMIE
}
