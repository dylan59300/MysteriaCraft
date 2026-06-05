package fr.vanadia.skills.impl;

import fr.vanadia.classes.ClassType;
import fr.vanadia.skills.Skill;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class BerserkSkill extends Skill {

    public BerserkSkill() {
        super("berserk", "Berserk", "Augmente vos degats pendant 10 secondes",
                ClassType.GUERRIER, 1, 0, 30000);
    }

    @Override
    public void execute(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 200, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0));
    }
}
