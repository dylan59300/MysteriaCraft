package fr.vanadia.quests;

import java.util.UUID;

public class QuestProgress {

    private final UUID playerUuid;
    private final String questId;
    private int progress;

    public QuestProgress(UUID playerUuid, String questId) {
        this.playerUuid = playerUuid;
        this.questId = questId;
        this.progress = 0;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getQuestId() {
        return questId;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public void increment() {
        this.progress++;
    }

    public void increment(int amount) {
        this.progress += amount;
    }
}
