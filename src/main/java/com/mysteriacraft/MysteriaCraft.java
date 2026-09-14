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
import com.mysteriacraft.economy.generator.GeneratorManager;
import com.mysteriacraft.economy.generator.GeneratorService;
import com.mysteriacraft.economy.generator.commands.GeneratorCommand;
import com.mysteriacraft.economy.generator.listeners.GeneratorListener;
import com.mysteriacraft.core.gui.MenuListener;
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
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import com.mysteriacraft.luckyblock.commands.LuckyBlockAdminCommand;
import com.mysteriacraft.luckyblock.commands.LuckyBlockCommand;
import com.mysteriacraft.luckyblock.listeners.LuckyBlockListener;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.customitems.CustomItemService;
import com.mysteriacraft.customitems.commands.CustomItemCommand;
import com.mysteriacraft.customitems.listeners.CustomItemListener;
import com.mysteriacraft.customitems.machine.MachineManager;
import com.mysteriacraft.customitems.machine.MachineService;
import com.mysteriacraft.customitems.machine.commands.MachineCommand;
import com.mysteriacraft.customitems.machine.listeners.MachineListener;
import com.mysteriacraft.customitems.miningmachine.MiningMachineManager;
import com.mysteriacraft.customitems.miningmachine.MiningMachineService;
import com.mysteriacraft.customitems.miningmachine.commands.MiningMachineCommand;
import com.mysteriacraft.customitems.miningmachine.listeners.MiningMachineListener;
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

    private ConfigManager generatorsConfig;
    private GeneratorManager generatorManager;
    private GeneratorService generatorService;

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

    private ConfigManager luckyBlocksConfig;
    private LuckyBlockManager luckyBlockManager;
    private LuckyBlockService luckyBlockService;

    private ConfigManager customItemsConfig;
    private CustomItemManager customItemManager;
    private CustomItemService customItemService;
    private MachineManager machineManager;
    private MachineService machineService;

    private ConfigManager miningMachineConfig;
    private MiningMachineManager miningMachineManager;
    private MiningMachineService miningMachineService;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        // ---- Core ----
        this.configManager = new ConfigManager(this, "config.yml");
        this.messagesManager = new ConfigManager(this, "messages.yml");
        this.messages = new MessageManager(messagesManager, configManager);

        this.database = new Database(this, configManager.get().getString("base-de-donnees.fichier", "database.db"));
        database.connect();

        // Listener generique pour tous les menus GUI (battlepass, quetes, pets...)
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);

        // ---- Module Economie ----
        setupEconomy();

        // RewardGiver est partage par BattlePass et Quetes pour eviter de dupliquer la logique de
        // distribution des recompenses. Le lien vers BattlePassService (pour BOOST_XP) est complete
        // dans setupBattlePass() via setBoosterHandler().
        this.rewardGiver = new RewardGiver(economyManager, messages);

        // ---- Module BattlePass ----
        setupBattlePass();

        // ---- Module Quetes ----
        setupQuests();

        // ---- Module Pets ----
        setupPets();

        // ---- Module LuckyBlock ----
        setupLuckyBlock();

        // ---- Module Custom Items ----
        setupCustomItems();

        // ---- Module Generateurs d'Argent ----
        setupGenerators();

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

    private void setupLuckyBlock() {
        this.luckyBlocksConfig = new ConfigManager(this, "luckyblocks.yml");
        this.luckyBlockManager = new LuckyBlockManager(this, database, luckyBlocksConfig);
        this.luckyBlockService = new LuckyBlockService(luckyBlockManager, economyManager, rewardGiver, messages);
        rewardGiver.setLuckyBlockGiveHandler(luckyBlockService);

        Bukkit.getPluginManager().registerEvents(new LuckyBlockListener(luckyBlockManager, luckyBlockService, messages), this);

        getCommand("luckyblock").setExecutor(new LuckyBlockCommand(luckyBlockManager, luckyBlockService, economyManager, messages));
        getCommand("luckyblockadmin").setExecutor(new LuckyBlockAdminCommand(luckyBlocksConfig, luckyBlockManager, messages));
    }

    private void setupCustomItems() {
        this.customItemsConfig = new ConfigManager(this, "custom_items.yml");
        this.customItemManager = new CustomItemManager(this, customItemsConfig);
        this.customItemService = new CustomItemService(customItemManager, economyManager, messages);
        rewardGiver.setCustomItemGiveHandler(customItemService);

        Bukkit.getPluginManager().registerEvents(new CustomItemListener(customItemService), this);

        getCommand("customitem").setExecutor(
                new CustomItemCommand(customItemsConfig, customItemManager, customItemService, messages));

        // Machine a Transformation : minerai -> Lucky Block (module luckyblock deja initialise avant celui-ci).
        this.machineManager = new MachineManager(this, database, customItemsConfig);
        this.machineService = new MachineService(this, machineManager, customItemManager, luckyBlockManager, questService, messages);
        Bukkit.getPluginManager().registerEvents(new MachineListener(machineManager, machineService, customItemManager, messages), this);
        getCommand("machine").setExecutor(new MachineCommand(customItemsConfig, machineManager, messages));

        // Effet de particules ambiant (densite/couleur selon le niveau de carburant), toutes les 5 secondes.
        Bukkit.getScheduler().runTaskTimer(this, machineService::tickAmbientParticles, 100L, 100L);

        // Machine a Miner : mine automatiquement un chunk entier au fil du temps, avec du carburant.
        this.miningMachineConfig = new ConfigManager(this, "mining_machine.yml");
        this.miningMachineManager = new MiningMachineManager(this, database, miningMachineConfig);
        this.miningMachineService = new MiningMachineService(miningMachineManager, customItemManager, messages);
        Bukkit.getPluginManager().registerEvents(
                new MiningMachineListener(miningMachineManager, miningMachineService, messages), this);
        getCommand("machineminiere").setExecutor(
                new MiningMachineCommand(miningMachineConfig, miningMachineManager, messages));

        // Traite blocs-par-tick blocs pour chaque machine active, chaque tick serveur.
        Bukkit.getScheduler().runTaskTimer(this, miningMachineService::tickAll, 20L, 1L);
    }

    private void setupGenerators() {
        this.generatorsConfig = new ConfigManager(this, "generateurs.yml");
        this.generatorManager = new GeneratorManager(this, database, generatorsConfig);
        this.generatorService = new GeneratorService(generatorManager, customItemManager, economyManager, messages);
        rewardGiver.setGeneratorGiveHandler(generatorService);

        Bukkit.getPluginManager().registerEvents(new GeneratorListener(generatorManager, generatorService, messages), this);
        getCommand("generateur").setExecutor(new GeneratorCommand(generatorsConfig, generatorManager, economyManager, messages));

        // Recalcul du stock accumule + auto-collecte (hopper) + rafraichissement de l'hologramme
        // (frequence configurable, generateurs.yml: tick-secondes).
        long periodTicks = generatorManager.getTickSeconds() * 20L;
        Bukkit.getScheduler().runTaskTimer(this, generatorService::tickGenerators, periodTicks, periodTicks);
    }

    @Override
    public void onDisable() {
        if (luckyBlockManager != null) {
            luckyBlockManager.unregisterRecipes();
        }
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
