package fr.vanadia.skills;

import fr.vanadia.Vanadia;
import fr.vanadia.classes.ClassType;
import fr.vanadia.player.PlayerData;
import fr.vanadia.skills.impl.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class SkillManager {

    private final Vanadia plugin;
    private final Map<String, Skill> skills;

    public SkillManager(Vanadia plugin) {
        this.plugin = plugin;
        this.skills = new HashMap<>();
        registerSkills();
    }

    private void registerSkills() {
        registerSkill(new BerserkSkill());
        registerSkill(new ShieldWallSkill());
        registerSkill(new FireballSkill());
        registerSkill(new HealSkill());
        registerSkill(new ArrowRainSkill());
        registerSkill(new SniperShotSkill());
        registerSkill(new BackstabSkill());
        registerSkill(new SmokeScreenSkill());
    }

    private void registerSkill(Skill skill) {
        skills.put(skill.getId(), skill);
    }

    public Skill getSkill(String id) {
        return skills.get(id);
    }

    public List<Skill> getSkillsForClass(ClassType classType) {
        return skills.values().stream()
                .filter(s -> s.getRequiredClass() == classType)
                .collect(Collectors.toList());
    }

    public List<Skill> getAvailableSkills(PlayerData data) {
        if (data.getClassType() == null) return Collections.emptyList();
        return skills.values().stream()
                .filter(s -> s.getRequiredClass() == data.getClassType())
                .filter(s -> data.getLevel() >= s.getRequiredLevel())
                .collect(Collectors.toList());
    }

    public boolean useSkill(Player player, String skillId) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getClassType() == null) return false;

        Skill skill = skills.get(skillId);
        if (skill == null) return false;
        if (skill.getRequiredClass() != data.getClassType()) return false;
        if (data.getLevel() < skill.getRequiredLevel()) return false;

        if (data.isSkillOnCooldown(skillId)) {
            long remaining = data.getSkillCooldownRemaining(skillId) / 1000;
            plugin.getMessageUtil().send(player, "skill-cooldown",
                    Map.of("time", String.valueOf(remaining)));
            return false;
        }

        if (skill.getManaCost() > 0 && !data.useMana(skill.getManaCost())) {
            plugin.getMessageUtil().send(player, "skill-no-mana",
                    Map.of("current", String.valueOf(data.getMana()),
                           "required", String.valueOf(skill.getManaCost())));
            return false;
        }

        skill.execute(player);
        data.setSkillCooldown(skillId, skill.getCooldownMillis());
        plugin.getMessageUtil().send(player, "skill-used",
                Map.of("skill", skill.getName()));
        return true;
    }
}
