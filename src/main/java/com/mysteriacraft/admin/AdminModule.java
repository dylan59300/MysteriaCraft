package com.mysteriacraft.admin;

import org.bukkit.Material;

/**
 * Description d'un module gerable depuis le panel admin unifie (voir AdminRegistry) : quelle
 * permission le montre, quelle commande recharge sa config, et (si le module a un editeur en jeu,
 * comme le BattlePass ou la Boutique) quelle commande l'ouvre.
 */
public record AdminModule(String label, Material icon, String permission, String reloadCommand, String editorCommand) {

    public AdminModule(String label, Material icon, String permission, String reloadCommand) {
        this(label, icon, permission, reloadCommand, null);
    }

    public boolean hasEditor() {
        return editorCommand != null;
    }
}
