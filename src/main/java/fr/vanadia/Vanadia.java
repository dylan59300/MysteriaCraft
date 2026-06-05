package fr.vanadia;

import fr.vanadia.classes.ClassManager;
import fr.vanadia.commands.*;
import fr.vanadia.dungeons.DungeonManager;
import fr.vanadia.items.ItemManager;
import fr.vanadia.listeners.CombatListener;
import fr.vanadia.listeners.PlayerListener;
import fr.vanadia.mobs.MobManager;
import fr.vanadia.player.PlayerManager;
import fr.vanadia.quests.QuestManager;
import fr.vanadia.skills.SkillManager;
import fr.vanadia.utils.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class Vanadia extends JavaPlugin {

    private static Vanadia instance;
    private PlayerManager playerManager;
    private ClassManager classManager;
    private SkillManager skillManager;
    private QuestManager questManager;
    private MobManager mobManager;
    private ItemManager itemManager;
    private DungeonManager dungeonManager;
    private MessageUtil messageUtil;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        initManagers();
        registerCommands();
        registerListeners();

        getLogger().info("Vanadia RPG Plugin v" + getDescription().getVersion() + " active!");
    }

    @Override
    public void onDisable() {
        if (playerManager != null) {
            playerManager.saveAllPlayers();
        }
        getLogger().info("Vanadia RPG Plugin desactive.");
    }

    private void initManagers() {
        messageUtil = new MessageUtil(this);
        classManager = new ClassManager(this);
        skillManager = new SkillManager(this);
        playerManager = new PlayerManager(this);
        questManager = new QuestManager(this);
        mobManager = new MobManager(this);
        itemManager = new ItemManager(this);
        dungeonManager = new DungeonManager(this);
    }

    private void registerCommands() {
        getCommand("class").setExecutor(new ClassCommand(this));
        getCommand("skills").setExecutor(new SkillsCommand(this));
        getCommand("quest").setExecutor(new QuestCommand(this));
        getCommand("profile").setExecutor(new ProfileCommand(this));
        getCommand("dungeon").setExecutor(new DungeonCommand(this));
        getCommand("vanadia").setExecutor(new VanadiaCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
    }

    public void reload() {
        reloadConfig();
        messageUtil.reload();
        classManager.reload();
        questManager.reload();
        mobManager.reload();
        dungeonManager.reload();
    }

    public static Vanadia getInstance() {
        return instance;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public MobManager getMobManager() {
        return mobManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }

    public MessageUtil getMessageUtil() {
        return messageUtil;
    }
}
