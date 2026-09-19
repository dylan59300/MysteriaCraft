package com.mysteriacraft.grappin;

import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Traite l'utilisation du Grappin : tire un rayon dans la ligne de mire du joueur, et s'il touche
 * un bloc dans la portee, projette le joueur vers ce point (traction). Cooldown par joueur, en
 * memoire uniquement. */
public class GrappinService {

    private final GrappinManager manager;
    private final MessageManager messages;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public GrappinService(GrappinManager manager, MessageManager messages) {
        this.manager = manager;
        this.messages = messages;
    }

    public void use(Player player) {
        UUID uuid = player.getUniqueId();
        long cooldownMillis = manager.getCooldownSeconds() * 1000L;
        Long last = lastUse.get(uuid);
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldownMillis) {
            return;
        }

        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), manager.getDistanceMax(),
                FluidCollisionMode.NEVER, true);
        if (result == null || result.getHitPosition() == null) {
            messages.send(player, "grappin.rien-vise");
            return;
        }

        lastUse.put(uuid, now);

        Vector direction = result.getHitPosition().toLocation(player.getWorld()).subtract(player.getLocation()).toVector();
        double distance = direction.length();
        if (distance < 0.5) {
            return;
        }
        Vector velocity = direction.normalize().multiply(Math.min(manager.getForceTraction(), distance / 2.0 + 0.5));
        player.setVelocity(velocity);
        player.setFallDistance(0f);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1f);
    }
}
