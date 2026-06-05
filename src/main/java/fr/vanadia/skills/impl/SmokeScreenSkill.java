package fr.vanadia.skills.impl;

import fr.vanadia.classes.ClassType;
import fr.vanadia.skills.Skill;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SmokeScreenSkill extends Skill {

    public SmokeScreenSkill() {
        super("smoke_screen", "Ecran de Fumee", "Devenir invisible et aveugler les ennemis proches",
                ClassType.ASSASSIN, 5, 0, 40000);
    }

    @Override
    public void execute(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));

        player.getWorld().spawnParticle(Particle.SMOKE_LARGE, player.getLocation(), 50, 2, 1, 2);

        for (var entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
            }
        }
    }
}
