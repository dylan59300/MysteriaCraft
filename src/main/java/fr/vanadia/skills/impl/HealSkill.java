package fr.vanadia.skills.impl;

import fr.vanadia.classes.ClassType;
import fr.vanadia.skills.Skill;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.Particle;

public class HealSkill extends Skill {

    public HealSkill() {
        super("heal", "Soin", "Restaure une grande partie de vos PV",
                ClassType.MAGE, 3, 35, 20000);
    }

    @Override
    public void execute(Player player) {
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double healAmount = maxHealth * 0.4;
        player.setHealth(Math.min(player.getHealth() + healAmount, maxHealth));
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 10);
    }
}
