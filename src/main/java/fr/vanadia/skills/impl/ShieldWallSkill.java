package fr.vanadia.skills.impl;

import fr.vanadia.classes.ClassType;
import fr.vanadia.skills.Skill;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ShieldWallSkill extends Skill {

    public ShieldWallSkill() {
        super("shield_wall", "Mur de Bouclier", "Reduit les degats recus pendant 8 secondes",
                ClassType.GUERRIER, 5, 0, 45000);
    }

    @Override
    public void execute(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 160, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 160, 0));
    }
}
