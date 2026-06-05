package fr.vanadia.mobs;

import org.bukkit.entity.EntityType;

import java.util.List;

public class CustomMob {

    private final String id;
    private final String displayName;
    private final EntityType baseEntity;
    private final double health;
    private final double damage;
    private final int xpReward;
    private final double spawnChance;
    private final List<String> drops;

    public CustomMob(String id, String displayName, EntityType baseEntity,
                     double health, double damage, int xpReward, double spawnChance,
                     List<String> drops) {
        this.id = id;
        this.displayName = displayName;
        this.baseEntity = baseEntity;
        this.health = health;
        this.damage = damage;
        this.xpReward = xpReward;
        this.spawnChance = spawnChance;
        this.drops = drops;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EntityType getBaseEntity() {
        return baseEntity;
    }

    public double getHealth() {
        return health;
    }

    public double getDamage() {
        return damage;
    }

    public int getXpReward() {
        return xpReward;
    }

    public double getSpawnChance() {
        return spawnChance;
    }

    public List<String> getDrops() {
        return drops;
    }
}
