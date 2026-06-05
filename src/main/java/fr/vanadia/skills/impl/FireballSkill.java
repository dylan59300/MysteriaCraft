package fr.vanadia.skills.impl;

import fr.vanadia.classes.ClassType;
import fr.vanadia.skills.Skill;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;

public class FireballSkill extends Skill {

    public FireballSkill() {
        super("fireball", "Boule de Feu", "Lance une boule de feu devastatrice",
                ClassType.MAGE, 1, 20, 10000);
    }

    @Override
    public void execute(Player player) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setYield(2.0f);
        fireball.setIsIncendiary(true);
    }
}
