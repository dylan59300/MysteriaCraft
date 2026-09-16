package com.mysteriacraft.talents.listeners;

import com.mysteriacraft.talents.TalentService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Chaque kill donne 1 point de talent. A la connexion, reapplique les bonus passifs deja
 * debloques (les attributs/effets de potion ne persistent pas d'une session a l'autre). */
public class TalentListener implements Listener {

    private final TalentService service;

    public TalentListener(TalentService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player player) {
            service.gainPoint(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.applyPassiveBonuses(event.getPlayer());
    }
}
