package fr.vanadia.skills.impl;

import fr.vanadia.classes.ClassType;
import fr.vanadia.skills.Skill;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;

public class SniperShotSkill extends Skill {

    public SniperShotSkill() {
        super("sniper_shot", "Tir de Precision", "Tire une fleche a tres haute vitesse qui inflige des degats massifs",
                ClassType.ARCHER, 5, 0, 35000);
    }

    @Override
    public void execute(Player player) {
        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setVelocity(player.getLocation().getDirection().multiply(5));
        arrow.setDamage(15.0);
        arrow.setCritical(true);
        arrow.setGlowing(true);
    }
}
