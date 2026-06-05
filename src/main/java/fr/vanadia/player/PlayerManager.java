package fr.vanadia.player;

import fr.vanadia.Vanadia;
import fr.vanadia.classes.ClassType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {

    private final Vanadia plugin;
    private final Map<UUID, PlayerData> playerDataMap;
    private final File playerDataFolder;

    public PlayerManager(Vanadia plugin) {
        this.plugin = plugin;
        this.playerDataMap = new HashMap<>();
        this.playerDataFolder = new File(plugin.getDataFolder(), "players");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    public PlayerData loadOrCreate(Player player) {
        UUID uuid = player.getUniqueId();
        if (playerDataMap.containsKey(uuid)) {
            return playerDataMap.get(uuid);
        }

        File file = new File(playerDataFolder, uuid + ".yml");
        PlayerData data;

        if (file.exists()) {
            data = loadFromFile(file, uuid, player.getName());
        } else {
            data = new PlayerData(uuid, player.getName());
        }

        playerDataMap.put(uuid, data);
        return data;
    }

    private PlayerData loadFromFile(File file, UUID uuid, String name) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        PlayerData data = new PlayerData(uuid, name);

        String className = config.getString("class");
        if (className != null) {
            ClassType classType = ClassType.fromString(className);
            data.setClassType(classType);
        }

        data.setLevel(config.getInt("level", 1));
        data.setXp(config.getInt("xp", 0));
        data.setMana(config.getInt("mana", 0));
        data.setMaxMana(config.getInt("max-mana", 0));

        for (String quest : config.getStringList("active-quests")) {
            data.addActiveQuest(quest);
        }
        for (String quest : config.getStringList("completed-quests")) {
            data.addCompletedQuest(quest);
        }

        return data;
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data == null) return;

        File file = new File(playerDataFolder, uuid + ".yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("name", data.getPlayerName());
        if (data.getClassType() != null) {
            config.set("class", data.getClassType().name());
        }
        config.set("level", data.getLevel());
        config.set("xp", data.getXp());
        config.set("mana", data.getMana());
        config.set("max-mana", data.getMaxMana());
        config.set("quests-completed", data.getQuestsCompleted());
        config.set("dungeons-completed", data.getDungeonsCompleted());
        config.set("active-quests", data.getActiveQuests());
        config.set("completed-quests", data.getCompletedQuests());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder les donnees de " + data.getPlayerName());
        }
    }

    public void saveAllPlayers() {
        for (UUID uuid : playerDataMap.keySet()) {
            savePlayer(uuid);
        }
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        playerDataMap.remove(uuid);
    }
}
