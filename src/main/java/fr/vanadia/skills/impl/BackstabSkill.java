package fr.vanadia.skills.impl;

import fr.vanadia.classes.ClassType;
import fr.vanadia.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class BackstabSkill extends Skill {

    public BackstabSkill() {
        super("backstab", "Coup dans le Dos", "Teleportation derriere la cible et coup critique",
                ClassType.ASSASSIN, 1, 0, 20000);
    }

    @Override
    public void execute(Player player) {
        Entity target = findNearestTarget(player);
        if (target instanceof LivingEntity livingTarget) {
            Location behind = livingTarget.getLocation().clone();
            behind.add(behind.getDirection().multiply(-1.5));
            behind.setY(livingTarget.getLocation().getY());
            player.teleport(behind);
            livingTarget.damage(12.0, player);
            player.getWorld().spawnParticle(Particle.CRIT, livingTarget.getLocation().add(0, 1, 0), 20);
        }
    }

    private Entity findNearestTarget(Player player) {
        Entity nearest = null;
        double minDist = 10.0;
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                double dist = entity.getLocation().distance(player.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = entity;
                }
            }
        }
        return nearest;
    }
}
