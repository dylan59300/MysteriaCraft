package fr.vanadia.skills;

import fr.vanadia.classes.ClassType;
import org.bukkit.entity.Player;

public abstract class Skill {

    private final String id;
    private final String name;
    private final String description;
    private final ClassType requiredClass;
    private final int requiredLevel;
    private final int manaCost;
    private final long cooldownMillis;

    protected Skill(String id, String name, String description, ClassType requiredClass,
                    int requiredLevel, int manaCost, long cooldownMillis) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.requiredClass = requiredClass;
        this.requiredLevel = requiredLevel;
        this.manaCost = manaCost;
        this.cooldownMillis = cooldownMillis;
    }

    public abstract void execute(Player player);

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ClassType getRequiredClass() {
        return requiredClass;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public int getManaCost() {
        return manaCost;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }
}
