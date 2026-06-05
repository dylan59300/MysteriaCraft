package fr.vanadia.dungeons;

import java.util.List;

public class Dungeon {

    private final String id;
    private final String displayName;
    private final int minLevel;
    private final int maxPlayers;
    private final int mobWaves;
    private final String bossId;
    private final int xpReward;
    private final List<String> itemRewards;

    public Dungeon(String id, String displayName, int minLevel, int maxPlayers,
                   int mobWaves, String bossId, int xpReward, List<String> itemRewards) {
        this.id = id;
        this.displayName = displayName;
        this.minLevel = minLevel;
        this.maxPlayers = maxPlayers;
        this.mobWaves = mobWaves;
        this.bossId = bossId;
        this.xpReward = xpReward;
        this.itemRewards = itemRewards;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getMobWaves() {
        return mobWaves;
    }

    public String getBossId() {
        return bossId;
    }

    public int getXpReward() {
        return xpReward;
    }

    public List<String> getItemRewards() {
        return itemRewards;
    }
}
