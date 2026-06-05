package fr.vanadia.dungeons;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class DungeonInstance {

    private final String dungeonId;
    private final Set<UUID> players;
    private final Location spawnLocation;
    private int currentWave;
    private boolean active;
    private boolean bossSpawned;

    public DungeonInstance(String dungeonId, Location spawnLocation) {
        this.dungeonId = dungeonId;
        this.players = new HashSet<>();
        this.spawnLocation = spawnLocation;
        this.currentWave = 0;
        this.active = true;
        this.bossSpawned = false;
    }

    public String getDungeonId() {
        return dungeonId;
    }

    public Set<UUID> getPlayers() {
        return players;
    }

    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public void nextWave() {
        this.currentWave++;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isBossSpawned() {
        return bossSpawned;
    }

    public void setBossSpawned(boolean bossSpawned) {
        this.bossSpawned = bossSpawned;
    }
}
