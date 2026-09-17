package com.mysteriacraft.admin;

import org.bukkit.Material;

/**
 * Description d'un module gerable depuis le panel admin unifie (voir AdminRegistry) : quelle
 * permission le montre, quelle commande recharge sa config, et (si le module a un editeur en jeu,
 * comme le BattlePass ou la Boutique) quelle commande l'ouvre.
 * sensible : demande une confirmation avant de recharger (modules ou une erreur de config a un
 * impact large, ex: Economie/Banque).
 * playerCommand : commande joueur a bloquer en mode maintenance (voir AdminMaintenanceManager),
 * si differente de reloadCommand (ex: BattlePass se recharge via "battlepassadmin" mais se joue
 * via "battlepass"). Laisser null utilise le premier mot de reloadCommand.
 */
public record AdminModule(String label, Material icon, String permission, String reloadCommand,
                           String editorCommand, String playerCommand, boolean sensible) {

    public AdminModule(String label, Material icon, String permission, String reloadCommand) {
        this(label, icon, permission, reloadCommand, null, null, false);
    }

    public AdminModule(String label, Material icon, String permission, String reloadCommand, String editorCommand) {
        this(label, icon, permission, reloadCommand, editorCommand, null, false);
    }

    public boolean hasEditor() {
        return editorCommand != null;
    }

    /** Commande joueur effectivement bloquee en mode maintenance. */
    public String commandeAGerer() {
        return playerCommand != null ? playerCommand : reloadCommand.split(" ")[0];
    }
}
