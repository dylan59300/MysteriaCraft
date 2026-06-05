package fr.vanadia.player;

import fr.vanadia.classes.ClassType;
import fr.vanadia.quests.Quest;

import java.util.*;

public class PlayerData {

    private final UUID uuid;
    private String playerName;
    private ClassType classType;
    private int level;
    private int xp;
    private int mana;
    private int maxMana;
    private int questsCompleted;
    private int dungeonsCompleted;
    private final List<String> activeQuests;
    private final List<String> completedQuests;
    private final Map<String, Long> skillCooldowns;

    public PlayerData(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.classType = null;
        this.level = 1;
        this.xp = 0;
        this.mana = 0;
        this.maxMana = 0;
        this.questsCompleted = 0;
        this.dungeonsCompleted = 0;
        this.activeQuests = new ArrayList<>();
        this.completedQuests = new ArrayList<>();
        this.skillCooldowns = new HashMap<>();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public ClassType getClassType() {
        return classType;
    }

    public void setClassType(ClassType classType) {
        this.classType = classType;
        if (classType != null) {
            this.maxMana = classType.getBaseMana();
            this.mana = this.maxMana;
        }
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public int getXpToNextLevel() {
        return (int) (100 * Math.pow(1.5, level - 1));
    }

    public boolean addXp(int amount) {
        this.xp += amount;
        boolean leveledUp = false;
        while (this.xp >= getXpToNextLevel()) {
            this.xp -= getXpToNextLevel();
            this.level++;
            leveledUp = true;
        }
        return leveledUp;
    }

    public int getMana() {
        return mana;
    }

    public void setMana(int mana) {
        this.mana = Math.max(0, Math.min(mana, maxMana));
    }

    public int getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(int maxMana) {
        this.maxMana = maxMana;
    }

    public boolean useMana(int amount) {
        if (mana < amount) return false;
        mana -= amount;
        return true;
    }

    public void regenMana(int amount) {
        this.mana = Math.min(mana + amount, maxMana);
    }

    public int getQuestsCompleted() {
        return questsCompleted;
    }

    public void incrementQuestsCompleted() {
        this.questsCompleted++;
    }

    public int getDungeonsCompleted() {
        return dungeonsCompleted;
    }

    public void incrementDungeonsCompleted() {
        this.dungeonsCompleted++;
    }

    public List<String> getActiveQuests() {
        return activeQuests;
    }

    public boolean hasActiveQuest(String questId) {
        return activeQuests.contains(questId);
    }

    public void addActiveQuest(String questId) {
        if (!activeQuests.contains(questId)) {
            activeQuests.add(questId);
        }
    }

    public void removeActiveQuest(String questId) {
        activeQuests.remove(questId);
    }

    public List<String> getCompletedQuests() {
        return completedQuests;
    }

    public void addCompletedQuest(String questId) {
        if (!completedQuests.contains(questId)) {
            completedQuests.add(questId);
        }
    }

    public Map<String, Long> getSkillCooldowns() {
        return skillCooldowns;
    }

    public boolean isSkillOnCooldown(String skillId) {
        Long cooldownEnd = skillCooldowns.get(skillId);
        if (cooldownEnd == null) return false;
        if (System.currentTimeMillis() >= cooldownEnd) {
            skillCooldowns.remove(skillId);
            return false;
        }
        return true;
    }

    public long getSkillCooldownRemaining(String skillId) {
        Long cooldownEnd = skillCooldowns.get(skillId);
        if (cooldownEnd == null) return 0;
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public void setSkillCooldown(String skillId, long durationMillis) {
        skillCooldowns.put(skillId, System.currentTimeMillis() + durationMillis);
    }
}
