package fr.vanadia.skills.impl;

import fr.vanadia.Vanadia;
import fr.vanadia.classes.ClassType;
import fr.vanadia.skills.Skill;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ArrowRainSkill extends Skill {

    public ArrowRainSkill() {
        super("arrow_rain", "Pluie de Fleches", "Fait pleuvoir des fleches sur la zone ciblee",
                ClassType.ARCHER, 1, 0, 25000);
    }

    @Override
    public void execute(Player player) {
        Location target = player.getTargetBlockExact(30) != null
                ? player.getTargetBlockExact(30).getLocation().add(0, 10, 0)
                : player.getLocation().add(player.getLocation().getDirection().multiply(10)).add(0, 10, 0);

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 10) {
                    cancel();
                    return;
                }
                Location spawnLoc = target.clone().add(
                        (Math.random() - 0.5) * 6,
                        0,
                        (Math.random() - 0.5) * 6
                );
                Arrow arrow = player.getWorld().spawnArrow(spawnLoc, new Vector(0, -1, 0), 2.0f, 0);
                arrow.setShooter(player);
                arrow.setDamage(4.0);
                count++;
            }
        }.runTaskTimer(Vanadia.getInstance(), 0L, 2L);
    }
}
