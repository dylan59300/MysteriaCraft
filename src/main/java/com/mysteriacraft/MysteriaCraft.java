package com.mysteriacraft;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.storage.Database;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.economy.PendingPaymentManager;
import com.mysteriacraft.economy.commands.BalTopCommand;
import com.mysteriacraft.economy.commands.BalanceCommand;
import com.mysteriacraft.economy.commands.EcoCommand;
import com.mysteriacraft.economy.commands.EcoReloadCommand;
import com.mysteriacraft.economy.commands.PayCommand;
import com.mysteriacraft.economy.commands.PayConfirmCommand;
import com.mysteriacraft.economy.listeners.EconomyJoinQuitListener;
import com.mysteriacraft.core.gui.MenuListener;
import com.mysteriacraft.kits.KitManager;
import com.mysteriacraft.kits.KitService;
import com.mysteriacraft.kits.commands.KitCommand;
import com.mysteriacraft.kits.commands.KitReloadCommand;
import com.mysteriacraft.crates.CrateManager;
import com.mysteriacraft.crates.CrateService;
import com.mysteriacraft.crates.commands.CrateCommand;
import com.mysteriacraft.crates.commands.CrateKeyCommand;
import com.mysteriacraft.crates.commands.CrateReloadCommand;
import com.mysteriacraft.battlepass.BattlePassManager;
import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.battlepass.commands.BattlePassAdminCommand;
import com.mysteriacraft.battlepass.commands.BattlePassCommand;
import com.mysteriacraft.battlepass.commands.BattlePassConfirmPremiumCommand;
import com.mysteriacraft.quests.QuestManager;
import com.mysteriacraft.quests.QuestService;
import com.mysteriacraft.quests.commands.QuestsAdminCommand;
import com.mysteriacraft.quests.commands.QuestsCommand;
import com.mysteriacraft.quests.listeners.QuestListener;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.pets.PetManager;
import com.mysteriacraft.pets.PetService;
import com.mysteriacraft.pets.commands.PetsAdminCommand;
import com.mysteriacraft.pets.commands.PetsCommand;
import com.mysteriacraft.pets.listeners.PetJoinQuitListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Classe principale du plugin MysteriaCraft.
 * Initialise le Core (config, messages, base de donnees) puis chaque module.
 */
public final class MysteriaCraft extends JavaPlugin {

    private ConfigManager configManager;
    private ConfigManager messagesManager;
    private MessageManager messages;
    private Database database;

    private EconomyManager economyManager;
    private PendingPaymentManager pendingPaymentManager;

    private ConfigManager kitsConfig;
    private KitManager kitManager;
    private KitService kitService;

    private ConfigManager cratesConfig;
    private CrateManager crateManager;
    private CrateService crateService;
    private RewardGiver rewardGiver;

    private ConfigManager battlepassConfig;
    private BattlePassManager battlePassManager;
    private BattlePassService battlePassService;

    private ConfigManager questsConfig;
    private QuestManager questManager;
    private QuestService questService;

    private ConfigManager petsConfig;
    private PetManager petManager;
    private PetService petService;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        // ---- Core ----
        this.configManager = new ConfigManager(this, "config.yml");
        this.messagesManager = new ConfigManager(this, "messages.yml");
        this.messages = new MessageManager(messagesManager, configManager);

        this.database = new Database(this, configManager.get().getString("base-de-donnees.fichier", "database.db"));
        database.connect();

        // Listener generique pour tous les menus GUI (kits, crates, battlepass, quetes, pets...)
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);

        // ---- Module Economie ----
        setupEconomy();

        // ---- Module Kits ----
        setupKits();

        // ---- Module Crates ----
        setupCrates();

        // ---- Module BattlePass ----
        setupBattlePass();

        // ---- Module Quetes ----
        setupQuests();

        // ---- Module Pets ----
        setupPets();

        getLogger().info("MysteriaCraft active en " + (System.currentTimeMillis() - start) + "ms.");
    }

    private void setupEconomy() {
        this.economyManager = new EconomyManager(this, database, configManager);
        this.pendingPaymentManager = new PendingPaymentManager();

        Bukkit.getPluginManager().registerEvents(new EconomyJoinQuitListener(this, economyManager), this);

        PayCommand payCommand = new PayCommand(this, economyManager, messages, configManager, pendingPaymentManager);

        getCommand("balance").setExecutor(new BalanceCommand(this, economyManager, messages));
        getCommand("pay").setExecutor(payCommand);
        getCommand("payconfirm").setExecutor(new PayConfirmCommand(payCommand, messages));
        getCommand("baltop").setExecutor(new BalTopCommand(this, economyManager, messages));
        getCommand("eco").setExecutor(new EcoCommand(this, economyManager, messages));
        getCommand("ecoreload").setExecutor(new EcoReloadCommand(configManager, messagesManager, messages));

        // Charge les comptes des joueurs deja connectes (rechargement du plugin)
        Bukkit.getScheduler().runTaskAsynchronously(this, () ->
                Bukkit.getOnlinePlayers().forEach(player ->
                        economyManager.loadAccount(player.getUniqueId(), player.getName())));
    }

    private void setupKits() {
        this.kitsConfig = new ConfigManager(this, "kits.yml");
        this.kitManager = new KitManager(this, database, kitsConfig);
        this.kitService = new KitService(this, kitManager, messages);

        getCommand("kit").setExecutor(new KitCommand(this, kitManager, kitService, messages));
        getCommand("kitreload").setExecutor(new KitReloadCommand(kitsConfig, kitManager, messages));
    }

    private void setupCrates() {
        this.cratesConfig = new ConfigManager(this, "crates.yml");
        this.crateManager = new CrateManager(this, database, cratesConfig);

        // RewardGiver est partage par Crates, BattlePass et Quetes : cree ici car il a besoin de
        // crateManager (pour les recompenses de type CLE_CAISSE). Le lien vers BattlePassService
        // (pour BOOST_XP) est complete dans setupBattlePass() via setBoosterHandler().
        this.rewardGiver = new RewardGiver(this, economyManager, crateManager, messages);

        this.crateService = new CrateService(this, crateManager, economyManager, rewardGiver, messages);

        getCommand("crate").setExecutor(new CrateCommand(this, crateManager, crateService, economyManager, messages));
        getCommand("cratekey").setExecutor(new CrateKeyCommand(this, crateManager, messages));
        getCommand("cratereload").setExecutor(new CrateReloadCommand(cratesConfig, crateManager, messages));
    }

    private void setupBattlePass() {
        this.battlepassConfig = new ConfigManager(this, "battlepass.yml");
        this.battlePassManager = new BattlePassManager(this, database, battlepassConfig);
        this.battlePassService = new BattlePassService(this, battlePassManager, economyManager, rewardGiver, messages);
        rewardGiver.setBoosterHandler(battlePassService);

        getCommand("battlepass").setExecutor(new BattlePassCommand(this, battlePassManager, battlePassService, messages));
        getCommand("battlepassadmin").setExecutor(
                new BattlePassAdminCommand(this, battlepassConfig, battlePassManager, battlePassService, messages));
        getCommand("battlepassconfirmpremium").setExecutor(
                new BattlePassConfirmPremiumCommand(battlePassService, messages));

        // XP passive au temps de jeu, en plus de l'xp donnee par les Quetes (QuestService#addXp).
        long xpPerMinute = battlePassManager.getXpPerMinute();
        if (xpPerMinute > 0) {
            Bukkit.getScheduler().runTaskTimer(this, () ->
                    Bukkit.getOnlinePlayers().forEach(player -> battlePassService.addXp(player, xpPerMinute)),
                    20L * 60, 20L * 60);
        }
    }

    private void setupQuests() {
        this.questsConfig = new ConfigManager(this, "quests.yml");
        this.questManager = new QuestManager(this, database, questsConfig);
        this.questService = new QuestService(this, questManager, battlePassService, rewardGiver, messages);

        Bukkit.getPluginManager().registerEvents(new QuestListener(questService), this);

        getCommand("quests").setExecutor(new QuestsCommand(this, questManager, messages));
        getCommand("questsadmin").setExecutor(new QuestsAdminCommand(this, questsConfig, questManager, messages));
    }

    private void setupPets() {
        this.petsConfig = new ConfigManager(this, "pets.yml");
        this.petManager = new PetManager(this, database, petsConfig);
        this.petService = new PetService(this, petManager, economyManager, messages);
        rewardGiver.setPetUnlockHandler(petService);

        Bukkit.getPluginManager().registerEvents(new PetJoinQuitListener(petService), this);

        getCommand("pets").setExecutor(new PetsCommand(this, petManager, petService, messages));
        getCommand("petsadmin").setExecutor(new PetsAdminCommand(petsConfig, petManager, messages));

        // Re-invoque le pet actif des joueurs deja connectes (rechargement du plugin)
        Bukkit.getOnlinePlayers().forEach(petService::respawnSavedPet);
    }

    @Override
    public void onDisable() {
        if (petService != null) {
            Bukkit.getOnlinePlayers().forEach(petService::despawnActive);
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("MysteriaCraft desactive.");
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public MessageManager getMessages() {
        return messages;
    }

    public Database getDatabase() {
        return database;
    }
}
