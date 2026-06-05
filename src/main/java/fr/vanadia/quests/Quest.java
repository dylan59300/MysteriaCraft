package fr.vanadia.quests;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public class Quest {

    private final String id;
    private final String name;
    private final String description;
    private final QuestType type;
    private final int requiredLevel;
    private final String target;
    private final int goal;
    private final int xpReward;
    private final List<String> itemRewards;

    public Quest(String id, String name, String description, QuestType type,
                 int requiredLevel, String target, int goal, int xpReward, List<String> itemRewards) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.requiredLevel = requiredLevel;
        this.target = target;
        this.goal = goal;
        this.xpReward = xpReward;
        this.itemRewards = itemRewards;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public QuestType getType() {
        return type;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public String getTarget() {
        return target;
    }

    public int getGoal() {
        return goal;
    }

    public int getXpReward() {
        return xpReward;
    }

    public List<String> getItemRewards() {
        return itemRewards;
    }

    public enum QuestType {
        KILL_MOB,
        COLLECT_ITEM,
        EXPLORE,
        CRAFT
    }
}
