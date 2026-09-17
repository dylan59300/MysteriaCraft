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
import com.mysteriacraft.battlepass.BattlePassManager;
import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.battlepass.commands.BattlePassAdminCommand;
import com.mysteriacraft.battlepass.commands.BattlePassCommand;
import com.mysteriacraft.battlepass.commands.BattlePassConfirmPremiumCommand;
import com.mysteriacraft.battlepass.listeners.ChatTitleListener;
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
import com.mysteriacraft.pets.listeners.PetCombatListener;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import com.mysteriacraft.luckyblock.commands.LuckyBlockAdminCommand;
import com.mysteriacraft.luckyblock.commands.LuckyBlockCommand;
import com.mysteriacraft.luckyblock.listeners.LuckyBlockListener;
import com.mysteriacraft.luckyblock.generator.LuckyBlockGeneratorManager;
import com.mysteriacraft.luckyblock.generator.LuckyBlockGeneratorService;
import com.mysteriacraft.luckyblock.generator.commands.LuckyBlockGeneratorCommand;
import com.mysteriacraft.luckyblock.generator.listeners.LuckyBlockGeneratorListener;
import com.mysteriacraft.dashboard.commands.MesMachinesCommand;
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
import com.mysteriacraft.customitems.generator.GeneratorManager;
import com.mysteriacraft.customitems.generator.GeneratorService;
import com.mysteriacraft.customitems.generator.commands.GeneratorCommand;
import com.mysteriacraft.customitems.generator.listeners.GeneratorListener;
import com.mysteriacraft.guide.GuideManager;
import com.mysteriacraft.guide.commands.GuideCommand;
import com.mysteriacraft.guide.listeners.GuideJoinListener;
import com.mysteriacraft.island.IslandManager;
import com.mysteriacraft.island.IslandService;
import com.mysteriacraft.island.commands.IslandCommand;
import com.mysteriacraft.island.listeners.IslandProtectionListener;
import com.mysteriacraft.marchand.MarchandManager;
import com.mysteriacraft.marchand.MarchandService;
import com.mysteriacraft.marchand.commands.MarchandAdminCommand;
import com.mysteriacraft.marchand.listeners.MarchandListener;
import com.mysteriacraft.banque.BanqueManager;
import com.mysteriacraft.banque.BanqueService;
import com.mysteriacraft.banque.commands.BanqueCommand;
import com.mysteriacraft.enchantement.EnchantementManager;
import com.mysteriacraft.enchantement.EnchantementService;
import com.mysteriacraft.enchantement.commands.EnchantementAdminCommand;
import com.mysteriacraft.enchantement.listeners.EnchantementListener;
import com.mysteriacraft.classes.ClasseManager;
import com.mysteriacraft.classes.ClasseService;
import com.mysteriacraft.classes.commands.ClasseCommand;
import com.mysteriacraft.classes.listeners.ClasseListener;
import com.mysteriacraft.encheres.EnchereManager;
import com.mysteriacraft.encheres.EnchereService;
import com.mysteriacraft.encheres.commands.EnchereCommand;
import com.mysteriacraft.shop.ShopManager;
import com.mysteriacraft.shop.PromotionManager;
import com.mysteriacraft.shop.TokenManager;
import com.mysteriacraft.shop.ShopService;
import com.mysteriacraft.shop.commands.ShopCommand;
import com.mysteriacraft.kit.KitManager;
import com.mysteriacraft.kit.KitService;
import com.mysteriacraft.kit.commands.KitCommand;
import com.mysteriacraft.rank.RankManager;
import com.mysteriacraft.rank.RankService;
import com.mysteriacraft.rank.commands.RankCommand;
import com.mysteriacraft.homes.HomeManager;
import com.mysteriacraft.homes.commands.HomeCommand;
import com.mysteriacraft.storage.PersonalStorageManager;
import com.mysteriacraft.storage.StorageService;
import com.mysteriacraft.storage.commands.SacCommand;
import com.mysteriacraft.storage.commands.CoffreFortCommand;
import com.mysteriacraft.storage.listeners.StorageListener;
import com.mysteriacraft.tools.commands.SortCommand;
import com.mysteriacraft.raffinerie.RaffinerieManager;
import com.mysteriacraft.raffinerie.RaffinerieService;
import com.mysteriacraft.raffinerie.commands.RaffinerieCommand;
import com.mysteriacraft.raffinerie.listeners.RaffinerieListener;
import com.mysteriacraft.recyclage.RecyclageManager;
import com.mysteriacraft.recyclage.RecyclageService;
import com.mysteriacraft.recyclage.commands.RecyclageCommand;
import com.mysteriacraft.grappin.GrappinManager;
import com.mysteriacraft.grappin.GrappinService;
import com.mysteriacraft.grappin.listeners.GrappinListener;
import com.mysteriacraft.gemmes.GemmeManager;
import com.mysteriacraft.gemmes.GemmeService;
import com.mysteriacraft.gemmes.commands.GemmeCommand;
import com.mysteriacraft.runes.RuneManager;
import com.mysteriacraft.runes.RuneService;
import com.mysteriacraft.runes.commands.RuneCommand;
import com.mysteriacraft.runes.listeners.RuneListener;
import com.mysteriacraft.marchenoir.MarcheNoirManager;
import com.mysteriacraft.marchenoir.MarcheNoirService;
import com.mysteriacraft.marchenoir.commands.MarcheNoirCommand;
import com.mysteriacraft.pets.dressage.PetTrainingManager;
import com.mysteriacraft.pets.dressage.commands.DressageCommand;
import com.mysteriacraft.pets.dressage.listeners.PetTrainingListener;
import com.mysteriacraft.talents.TalentManager;
import com.mysteriacraft.talents.TalentService;
import com.mysteriacraft.talents.commands.TalentCommand;
import com.mysteriacraft.talents.listeners.TalentListener;
import com.mysteriacraft.metiers.MetierManager;
import com.mysteriacraft.metiers.MetierService;
import com.mysteriacraft.metiers.commands.MetierCommand;
import com.mysteriacraft.metiers.listeners.MetierListener;
import com.mysteriacraft.hybride.HybridGeneratorManager;
import com.mysteriacraft.hybride.HybridGeneratorService;
import com.mysteriacraft.hybride.commands.HybridGeneratorCommand;
import com.mysteriacraft.hybride.listeners.HybridGeneratorListener;
import com.mysteriacraft.mobscustom.MobManager;
import com.mysteriacraft.mobscustom.commands.MobCommand;
import com.mysteriacraft.mobscustom.listeners.MobListener;
import com.mysteriacraft.etabli.EtabliManager;
import com.mysteriacraft.etabli.EtabliService;
import com.mysteriacraft.etabli.commands.EtabliCommand;
import com.mysteriacraft.etabli.listeners.EtabliListener;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

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
    private ConfigManager luckyBlockGeneratorConfig;
    private LuckyBlockGeneratorManager luckyBlockGeneratorManager;
    private LuckyBlockGeneratorService luckyBlockGeneratorService;

    private ConfigManager customItemsConfig;
    private CustomItemManager customItemManager;
    private CustomItemService customItemService;
    private MachineManager machineManager;
    private MachineService machineService;

    private ConfigManager miningMachineConfig;
    private MiningMachineManager miningMachineManager;
    private MiningMachineService miningMachineService;

    private ConfigManager generatorsConfig;
    private GeneratorManager generatorManager;
    private GeneratorService generatorService;

    private ConfigManager guideConfig;
    private GuideManager guideManager;

    private ConfigManager islandsConfig;
    private IslandManager islandManager;
    private IslandService islandService;
    private ConfigManager marchandConfig;
    private MarchandManager marchandManager;
    private MarchandService marchandService;
    private ConfigManager banqueConfig;
    private BanqueManager banqueManager;
    private BanqueService banqueService;
    private ConfigManager enchantementConfig;
    private EnchantementManager enchantementManager;
    private EnchantementService enchantementService;
    private ConfigManager classesConfig;
    private ClasseManager classeManager;
    private ClasseService classeService;
    private ConfigManager encheresConfig;
    private EnchereManager enchereManager;
    private EnchereService enchereService;
    private ConfigManager boutiqueConfig;
    private ConfigManager promotionsConfig;
    private ShopManager shopManager;
    private ShopService shopService;
    private PromotionManager promotionManager;
    private TokenManager tokenManager;
    private ConfigManager kitsConfig;
    private KitManager kitManager;
    private KitService kitService;
    private ConfigManager ranksConfig;
    private RankManager rankManager;
    private RankService rankService;
    private ConfigManager homesConfig;
    private HomeManager homeManager;
    private ConfigManager storageConfig;
    private PersonalStorageManager personalStorageManager;
    private StorageService storageService;
    private ConfigManager raffinerieConfig;
    private RaffinerieManager raffinerieManager;
    private RaffinerieService raffinerieService;
    private ConfigManager recyclageConfig;
    private RecyclageManager recyclageManager;
    private RecyclageService recyclageService;
    private ConfigManager grappinConfig;
    private GrappinManager grappinManager;
    private GrappinService grappinService;
    private ConfigManager gemmesConfig;
    private GemmeManager gemmeManager;
    private GemmeService gemmeService;
    private ConfigManager runesConfig;
    private RuneManager runeManager;
    private RuneService runeService;
    private ConfigManager marcheNoirConfig;
    private MarcheNoirManager marcheNoirManager;
    private MarcheNoirService marcheNoirService;
    private ConfigManager dressageConfig;
    private PetTrainingManager petTrainingManager;
    private ConfigManager talentsConfig;
    private TalentManager talentManager;
    private TalentService talentService;
    private ConfigManager metiersConfig;
    private MetierManager metierManager;
    private MetierService metierService;
    private ConfigManager hybrideConfig;
    private HybridGeneratorManager hybridGeneratorManager;
    private HybridGeneratorService hybridGeneratorService;
    private ConfigManager mobsCustomConfig;
    private MobManager mobManager;
    private ConfigManager etabliConfig;
    private EtabliManager etabliManager;
    private EtabliService etabliService;

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

        // ---- Module Custom Items (minerais/objets, Machine a Transformation, Machine a Miner,
        // Generateurs d'Argent) ----
        setupCustomItems();
        setupGenerators();

        // ---- Module Generateur de Lucky Block (depend de luckyBlockManager + customItemManager,
        // tous les deux deja initialises ci-dessus) ----
        setupLuckyBlockGenerator();

        // ---- Module Dashboard ("/mesmachines" : recapitulatif de tous les generateurs/machines
        // possedes par le joueur, depend de tous les modules ci-dessus) ----
        setupDashboard();

        // ---- Module Guide ----
        setupGuide();

        // ---- Module Iles (skyblock) ----
        setupIslands();

        // ---- Module PNJ Marchand ----
        setupMarchand();

        // ---- Module Table d'Enchantement Custom (depend de customItemManager) ----
        setupEnchantement();

        // ---- Module Classes/Metiers (depend d'economyManager) ----
        setupClasses();

        // ---- Module Hotel des Ventes (depend d'economyManager) ----
        setupEncheres();

        // ---- Module Prestige (depend d'economyManager) ----
        setupRanks();

        // ---- Module Talents (depend du rewardGiver indirectement via ShopService) ----
        setupTalents();

        // ---- Module Boutique (depend d'economyManager, rewardGiver, customItemManager,
        // rankManager ET talentManager pour le bonus de vente cumulatif) ----
        setupBoutique();

        // ---- Module Kits (depend de rewardGiver) ----
        setupKits();

        // ---- Module Homes (depend de rankManager pour le bonus de limite) ----
        setupHomes();

        // ---- Module Sac/Coffre-fort (depend de customItemManager et economyManager) ----
        setupStorage();

        // ---- Outils divers (/trier) ----
        setupTools();

        // ---- Raffinerie ----
        setupRaffinerie();

        // ---- Recyclage (depend de customItemManager) ----
        setupRecyclage();

        // ---- Grappin (depend de customItemManager) ----
        setupGrappin();

        // ---- Gemmes (depend de customItemManager) ----
        setupGemmes();

        // ---- Runes (depend de customItemManager) ----
        setupRunes();

        // ---- Marche Noir (depend d'economyManager et rewardGiver) ----
        setupMarcheNoir();

        // ---- Dressage (depend de petService, deja initialise par setupPets()) ----
        setupDressage();

        // ---- Metiers (independant) ----
        setupMetiers();

        // ---- Generateur Hybride (module standalone, independant de GeneratorManager) ----
        setupHybridGenerator();

        // ---- Mobs Custom (depend de rewardGiver) ----
        setupMobsCustom();

        // ---- Etabli Ameliore (depend de customItemManager, rewardGiver et metierService) ----
        setupEtabli();

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

        // ---- Module Banque (solde separe avec interets, depend d'EconomyManager) ----
        this.banqueConfig = new ConfigManager(this, "banque.yml");
        this.banqueManager = new BanqueManager(this, database, banqueConfig);
        this.banqueService = new BanqueService(this, banqueManager, economyManager, messages);
        getCommand("banque").setExecutor(new BanqueCommand(banqueConfig, banqueManager, banqueService, messages));
    }

    private void setupBattlePass() {
        this.battlepassConfig = new ConfigManager(this, "battlepass.yml");
        this.battlePassManager = new BattlePassManager(this, database, battlepassConfig);
        battlePassManager.checkAndArchiveSeasonIfNeeded();
        this.battlePassService = new BattlePassService(this, battlePassManager, economyManager, rewardGiver, messages);
        rewardGiver.setBoosterHandler(battlePassService);
        rewardGiver.setTitleUnlockHandler(battlePassService);
        Bukkit.getPluginManager().registerEvents(new ChatTitleListener(battlePassService), this);

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
        questManager.setBattlePassLevelProvider(uuid -> battlePassManager.computeLevel(battlePassManager.getXp(uuid)));
        this.questService = new QuestService(this, questManager, battlePassService, rewardGiver, economyManager, messages);

        // Verifications periodiques (biomes visites, objets custom differents possedes) : voir
        // QuestType.EXPLORE_BIOME et COLLECT_DISTINCT_CUSTOM_ITEMS.
        Bukkit.getScheduler().runTaskTimer(this, questService::tickExploration, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, questService::tickCollection, 100L, 100L);

        Bukkit.getPluginManager().registerEvents(new QuestListener(questService), this);

        getCommand("quests").setExecutor(new QuestsCommand(this, questManager, questService, questsConfig, messages));
        getCommand("questsadmin").setExecutor(new QuestsAdminCommand(this, questsConfig, questManager, messages));
    }

    private void setupPets() {
        this.petsConfig = new ConfigManager(this, "pets.yml");
        this.petManager = new PetManager(this, database, petsConfig);
        this.petService = new PetService(this, petManager, economyManager, messages);
        rewardGiver.setPetUnlockHandler(petService);

        Bukkit.getPluginManager().registerEvents(new PetJoinQuitListener(petService), this);
        Bukkit.getPluginManager().registerEvents(new PetCombatListener(petService), this);

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
        LuckyBlockAdminCommand luckyBlockAdminCommand = new LuckyBlockAdminCommand(luckyBlocksConfig, luckyBlockManager, luckyBlockService, messages);
        getCommand("luckyblockadmin").setExecutor(luckyBlockAdminCommand);
        getCommand("luckyblockadmin").setTabCompleter(luckyBlockAdminCommand);
    }

    private void setupCustomItems() {
        this.customItemsConfig = new ConfigManager(this, "custom_items.yml");
        this.customItemManager = new CustomItemManager(this, customItemsConfig);
        questService.setCustomItemManager(customItemManager);
        this.customItemService = new CustomItemService(customItemManager, economyManager, messages);
        rewardGiver.setCustomItemGiveHandler(customItemService);

        Bukkit.getPluginManager().registerEvents(new CustomItemListener(customItemService, customItemManager), this);

        CustomItemCommand customItemCommand = new CustomItemCommand(customItemsConfig, customItemManager, customItemService, messages);
        getCommand("customitem").setExecutor(customItemCommand);
        getCommand("customitem").setTabCompleter(customItemCommand);

        // Machine a Transformation : minerai -> Lucky Block (module luckyblock deja initialise avant celui-ci).
        this.machineManager = new MachineManager(this, database, customItemsConfig);
        this.machineService = new MachineService(this, machineManager, customItemManager, luckyBlockManager, questService, messages);
        Bukkit.getPluginManager().registerEvents(new MachineListener(machineManager, machineService, customItemManager, messages), this);
        getCommand("machine").setExecutor(new MachineCommand(customItemsConfig, machineManager, machineService, messages));

        // Effet de particules ambiant (densite/couleur selon le niveau de carburant), toutes les 5 secondes.
        Bukkit.getScheduler().runTaskTimer(this, machineService::tickAmbientParticles, 100L, 100L);

        // Machine a Miner : mine automatiquement un chunk entier au fil du temps, avec du carburant.
        this.miningMachineConfig = new ConfigManager(this, "mining_machine.yml");
        this.miningMachineManager = new MiningMachineManager(this, database, miningMachineConfig);
        this.miningMachineService = new MiningMachineService(this, miningMachineManager, customItemManager, messages);
        // Injecte apres-coup (setter) : la Machine a Miner n'existe pas encore quand machineService
        // est construit plus haut dans cette meme methode (voir aussi setupGenerators pour generatorManager).
        machineService.setMiningMachineManager(miningMachineManager);
        Bukkit.getPluginManager().registerEvents(
                new MiningMachineListener(miningMachineManager, miningMachineService, messages), this);
        getCommand("machineminiere").setExecutor(
                new MiningMachineCommand(miningMachineConfig, miningMachineManager, messages));

        // Traite blocs-par-tick blocs pour chaque machine active, chaque tick serveur.
        Bukkit.getScheduler().runTaskTimer(this, miningMachineService::tickAll, 20L, 1L);

        // MachineManager et MiningMachineManager n'ont pas de type commun : ce petit adaptateur
        // choisit laquelle appeler selon machineId ("transformation" ou "miniere") pour le type
        // de recompense MACHINE (Lucky Block/BattlePass/Quetes).
        rewardGiver.setMachineGiveHandler((player, machineId, amount) -> {
            ItemStack item = "miniere".equalsIgnoreCase(machineId)
                    ? miningMachineManager.createMachineItem(amount)
                    : machineManager.createMachineItem(amount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                messages.send(player, "general.inventaire-plein");
            }
        });
    }

    private void setupGenerators() {
        this.generatorsConfig = new ConfigManager(this, "generateurs.yml");
        this.generatorManager = new GeneratorManager(this, database, generatorsConfig, customItemManager);
        this.generatorService = new GeneratorService(generatorManager, customItemManager, economyManager, messages);
        rewardGiver.setGeneratorGiveHandler(generatorService);
        // Injecte apres-coup (setter) : le module Generateurs n'existe pas encore quand machineService
        // est construit dans setupCustomItems() (appelee avant setupGenerators()).
        machineService.setGeneratorManager(generatorManager);

        Bukkit.getPluginManager().registerEvents(new GeneratorListener(generatorManager, generatorService, messages), this);
        GeneratorCommand generatorCommand = new GeneratorCommand(generatorsConfig, generatorManager, economyManager, messages);
        getCommand("generateur").setExecutor(generatorCommand);
        getCommand("generateur").setTabCompleter(generatorCommand);

        // Recalcul du stock accumule + auto-collecte (hopper) + rafraichissement de l'hologramme
        // (frequence configurable, generateurs.yml: tick-secondes).
        long periodTicks = generatorManager.getTickSeconds() * 20L;
        Bukkit.getScheduler().runTaskTimer(this, generatorService::tickGenerators, periodTicks, periodTicks);
    }

    private void setupLuckyBlockGenerator() {
        this.luckyBlockGeneratorConfig = new ConfigManager(this, "luckyblock_generateur.yml");
        this.luckyBlockGeneratorManager = new LuckyBlockGeneratorManager(this, database, luckyBlockGeneratorConfig, luckyBlockManager);
        this.luckyBlockGeneratorService = new LuckyBlockGeneratorService(
                luckyBlockGeneratorManager, customItemManager, luckyBlockManager, messages);

        Bukkit.getPluginManager().registerEvents(
                new LuckyBlockGeneratorListener(luckyBlockGeneratorManager, luckyBlockGeneratorService, messages), this);

        LuckyBlockGeneratorCommand luckyBlockGeneratorCommand = new LuckyBlockGeneratorCommand(
                luckyBlockGeneratorConfig, luckyBlockGeneratorManager, luckyBlockManager, messages);
        getCommand("generateurlb").setExecutor(luckyBlockGeneratorCommand);
        getCommand("generateurlb").setTabCompleter(luckyBlockGeneratorCommand);

        // Recalcule la production due + depose dans le conteneur colle, toutes les 5 secondes
        // (assez frequent pour rester reactif sans jamais impacter les performances, comme les
        // autres blocs "actifs" du plugin).
        Bukkit.getScheduler().runTaskTimer(this,
                () -> luckyBlockGeneratorService.tickAll(luckyBlockGeneratorManager.getActiveGeneratorLocations()),
                100L, 100L);

        // Permet au Generateur de Lucky Block d'etre distribue comme n'importe quelle Reward
        // generique (type GENERATEUR_LUCKYBLOCK : LuckyBlock/BattlePass/Quetes/Marchand/paliers d'ile).
        rewardGiver.setGeneratorLuckyBlockGiveHandler((player, familyId, amount) -> {
            var family = luckyBlockManager.getFamily(familyId);
            if (family == null) {
                return;
            }
            ItemStack item = luckyBlockGeneratorManager.createItem(family, amount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                messages.send(player, "general.inventaire-plein");
            }
        });

        // Injecte apres-coup (setter) : le module Generateur de Lucky Block n'existe pas encore
        // quand machineService est construit dans setupCustomItems() (appelee avant celle-ci).
        machineService.setLuckyBlockGeneratorManager(luckyBlockGeneratorManager);
    }

    private void setupDashboard() {
        getCommand("mesmachines").setExecutor(new MesMachinesCommand(
                generatorManager, luckyBlockGeneratorManager, miningMachineManager, economyManager, messages));
    }

    private void setupGuide() {
        this.guideConfig = new ConfigManager(this, "guide.yml");
        this.guideManager = new GuideManager(this, database, guideConfig);

        getCommand("guide").setExecutor(new GuideCommand(guideConfig, guideManager, messages));
        Bukkit.getPluginManager().registerEvents(new GuideJoinListener(this, guideManager, messages), this);
    }

    private void setupIslands() {
        this.islandsConfig = new ConfigManager(this, "islands.yml");
        this.islandManager = new IslandManager(this, database, islandsConfig);
        this.islandService = new IslandService(islandManager, economyManager, rewardGiver, battlePassService,
                customItemManager, machineManager, generatorManager, questService, messages);
        rewardGiver.setIslandUpgradeGiveHandler(islandService);

        Bukkit.getPluginManager().registerEvents(new IslandProtectionListener(islandManager, islandService, messages), this);
        getCommand("ile").setExecutor(new IslandCommand(islandsConfig, islandManager, islandService, questManager, messages));

        // Jauge de particules le long du perimetre de l'ile de chaque joueur present, toutes les 3 secondes.
        Bukkit.getScheduler().runTaskTimer(this, islandService::tickBorders, 60L, 60L);
    }

    private void setupMarchand() {
        this.marchandConfig = new ConfigManager(this, "marchand.yml");
        this.marchandManager = new MarchandManager(this, marchandConfig);
        this.marchandService = new MarchandService(this, database, customItemManager, rewardGiver, messages);

        Bukkit.getPluginManager().registerEvents(
                new MarchandListener(marchandManager, marchandService, customItemManager, messages), this);
        getCommand("pnjmarchand").setExecutor(new MarchandAdminCommand(marchandConfig, marchandManager, messages));
    }

    private void setupEnchantement() {
        this.enchantementConfig = new ConfigManager(this, "enchantement.yml");
        this.enchantementManager = new EnchantementManager(this, enchantementConfig);
        this.enchantementService = new EnchantementService(enchantementManager, messages);
        Bukkit.getPluginManager().registerEvents(
                new EnchantementListener(enchantementManager, enchantementService, customItemManager, messages), this);
        getCommand("enchantementadmin").setExecutor(
                new EnchantementAdminCommand(enchantementConfig, enchantementManager, messages));
    }

    private void setupClasses() {
        this.classesConfig = new ConfigManager(this, "classes.yml");
        this.classeManager = new ClasseManager(this, database, classesConfig);
        this.classeService = new ClasseService(this, classeManager, economyManager, messages);

        Bukkit.getPluginManager().registerEvents(new ClasseListener(classeService), this);
        getCommand("classe").setExecutor(new ClasseCommand(this, classesConfig, classeManager, classeService, messages));

        // Reapplique l'effet de classe de chaque joueur en ligne avant l'expiration naturelle
        // de la duree de potion (voir ClasseService#EFFECT_DURATION_TICKS), toutes les 70 minutes.
        Bukkit.getScheduler().runTaskTimer(this, classeService::reapplyAllOnline, 20L * 60 * 70, 20L * 60 * 70);
    }

    private void setupEncheres() {
        this.encheresConfig = new ConfigManager(this, "encheres.yml");
        this.enchereManager = new EnchereManager(this, database, encheresConfig);
        this.enchereService = new EnchereService(this, enchereManager, economyManager, messages);
        getCommand("hoteldesventes").setExecutor(
                new EnchereCommand(this, encheresConfig, enchereManager, enchereService, messages));
    }

    private void setupRanks() {
        this.ranksConfig = new ConfigManager(this, "ranks.yml");
        this.rankManager = new RankManager(this, database, ranksConfig);
        this.rankService = new RankService(this, rankManager, economyManager, messages);
        getCommand("prestige").setExecutor(new RankCommand(rankService, messages));
    }

    private void setupTalents() {
        this.talentsConfig = new ConfigManager(this, "talents.yml");
        this.talentManager = new TalentManager(this, database, talentsConfig);
        this.talentService = new TalentService(this, talentManager, messages);
        Bukkit.getPluginManager().registerEvents(new TalentListener(talentService), this);
        getCommand("talents").setExecutor(new TalentCommand(this, talentsConfig, talentManager, talentService, messages));
    }

    private void setupBoutique() {
        this.boutiqueConfig = new ConfigManager(this, "boutique.yml");
        this.promotionsConfig = new ConfigManager(this, "promotions.yml");
        this.shopManager = new ShopManager(this, database, boutiqueConfig);
        this.promotionManager = new PromotionManager(this, database, promotionsConfig);
        this.tokenManager = new TokenManager(this, database, promotionsConfig);
        this.shopService = new ShopService(shopManager, economyManager, rewardGiver, customItemManager, rankManager,
                talentManager, promotionManager, tokenManager, messages);
        getCommand("boutique").setExecutor(new ShopCommand(this, boutiqueConfig, promotionsConfig, shopManager,
                shopService, promotionManager, economyManager, messages));
    }

    private void setupKits() {
        this.kitsConfig = new ConfigManager(this, "kits.yml");
        this.kitManager = new KitManager(this, database, kitsConfig);
        this.kitService = new KitService(this, kitManager, rewardGiver, messages);
        KitCommand kitCommand = new KitCommand(this, kitsConfig, kitManager, kitService, messages);
        getCommand("kit").setExecutor(kitCommand);
        getCommand("kit").setTabCompleter(kitCommand);
    }

    private void setupHomes() {
        this.homesConfig = new ConfigManager(this, "homes.yml");
        this.homeManager = new HomeManager(this, database, homesConfig, rankManager);
        HomeCommand homeCommand = new HomeCommand(this, homeManager, messages);
        getCommand("home").setExecutor(homeCommand);
        getCommand("home").setTabCompleter(homeCommand);
    }

    private void setupStorage() {
        this.storageConfig = new ConfigManager(this, "storage.yml");
        this.personalStorageManager = new PersonalStorageManager(this, database);
        this.storageService = new StorageService(this, personalStorageManager, storageConfig, customItemManager, economyManager, messages);
        Bukkit.getPluginManager().registerEvents(new StorageListener(storageService), this);
        getCommand("sac").setExecutor(new SacCommand(storageService, messages));
        getCommand("coffrefort").setExecutor(new CoffreFortCommand(storageService, messages));
    }

    private void setupTools() {
        getCommand("trier").setExecutor(new SortCommand(messages));
    }

    private void setupRaffinerie() {
        this.raffinerieConfig = new ConfigManager(this, "raffinerie.yml");
        this.raffinerieManager = new RaffinerieManager(this, raffinerieConfig);
        this.raffinerieService = new RaffinerieService(raffinerieManager, messages);
        Bukkit.getPluginManager().registerEvents(new RaffinerieListener(raffinerieManager, raffinerieService), this);
        getCommand("raffinerie").setExecutor(new RaffinerieCommand(raffinerieConfig, raffinerieManager, messages));
    }

    private void setupRecyclage() {
        this.recyclageConfig = new ConfigManager(this, "recyclage.yml");
        this.recyclageManager = new RecyclageManager(this, recyclageConfig);
        this.recyclageService = new RecyclageService(recyclageManager, customItemManager, messages);
        getCommand("recycler").setExecutor(new RecyclageCommand(recyclageConfig, recyclageManager, recyclageService, messages));
    }

    private void setupGrappin() {
        this.grappinConfig = new ConfigManager(this, "grappin.yml");
        this.grappinManager = new GrappinManager(grappinConfig);
        this.grappinService = new GrappinService(grappinManager, messages);
        Bukkit.getPluginManager().registerEvents(new GrappinListener(grappinManager, grappinService, customItemManager), this);
    }

    private void setupGemmes() {
        this.gemmesConfig = new ConfigManager(this, "gemmes.yml");
        this.gemmeManager = new GemmeManager(this, gemmesConfig);
        this.gemmeService = new GemmeService(gemmeManager, customItemManager, messages);
        getCommand("gemme").setExecutor(new GemmeCommand(gemmesConfig, gemmeManager, gemmeService, messages));
    }

    private void setupRunes() {
        this.runesConfig = new ConfigManager(this, "runes.yml");
        this.runeManager = new RuneManager(this, runesConfig);
        this.runeService = new RuneService(runeManager, customItemManager, messages);
        Bukkit.getPluginManager().registerEvents(new RuneListener(runeService), this);
        getCommand("rune").setExecutor(new RuneCommand(runesConfig, runeManager, runeService, messages));
    }

    private void setupMarcheNoir() {
        this.marcheNoirConfig = new ConfigManager(this, "marche_noir.yml");
        this.marcheNoirManager = new MarcheNoirManager(this, marcheNoirConfig);
        this.marcheNoirService = new MarcheNoirService(marcheNoirManager, economyManager, rewardGiver, messages);
        getCommand("marchenoir").setExecutor(
                new MarcheNoirCommand(marcheNoirConfig, marcheNoirManager, marcheNoirService, economyManager, messages));
    }

    private void setupDressage() {
        this.dressageConfig = new ConfigManager(this, "dressage.yml");
        this.petTrainingManager = new PetTrainingManager(this, database, dressageConfig);
        petService.setTrainingManager(petTrainingManager);
        Bukkit.getPluginManager().registerEvents(new PetTrainingListener(this, petTrainingManager, petService, messages), this);
        getCommand("dressage").setExecutor(new DressageCommand(this, petTrainingManager, messages));
    }

    private void setupMetiers() {
        this.metiersConfig = new ConfigManager(this, "metiers.yml");
        this.metierManager = new MetierManager(this, database, metiersConfig);
        this.metierService = new MetierService(this, metierManager, economyManager, messages);
        Bukkit.getPluginManager().registerEvents(new MetierListener(this, metierService), this);
        MetierCommand metierCommand = new MetierCommand(metiersConfig, metierManager, metierService, messages);
        getCommand("metier").setExecutor(metierCommand);
        getCommand("metier").setTabCompleter(metierCommand);
    }

    private void setupHybridGenerator() {
        this.hybrideConfig = new ConfigManager(this, "hybride_generateur.yml");
        this.hybridGeneratorManager = new HybridGeneratorManager(this, database, hybrideConfig);
        this.hybridGeneratorService = new HybridGeneratorService(hybridGeneratorManager, customItemManager, messages);
        Bukkit.getPluginManager().registerEvents(new HybridGeneratorListener(hybridGeneratorManager, hybridGeneratorService), this);
        HybridGeneratorCommand hybridCommand = new HybridGeneratorCommand(hybrideConfig, hybridGeneratorManager, messages);
        getCommand("generateurhybride").setExecutor(hybridCommand);
        getCommand("generateurhybride").setTabCompleter(hybridCommand);

        Bukkit.getScheduler().runTaskTimer(this, hybridGeneratorService::tickGenerators,
                20L * hybrideConfig.get().getInt("tick-secondes", 5), 20L * hybrideConfig.get().getInt("tick-secondes", 5));
    }

    private void setupMobsCustom() {
        this.mobsCustomConfig = new ConfigManager(this, "mobs_custom.yml");
        this.mobManager = new MobManager(this, mobsCustomConfig);
        Bukkit.getPluginManager().registerEvents(new MobListener(mobManager, rewardGiver), this);
        MobCommand mobCommand = new MobCommand(mobsCustomConfig, mobManager, messages);
        getCommand("mobcustom").setExecutor(mobCommand);
        getCommand("mobcustom").setTabCompleter(mobCommand);
    }

    private void setupEtabli() {
        this.etabliConfig = new ConfigManager(this, "etabli.yml");
        this.etabliManager = new EtabliManager(this, database, etabliConfig);
        this.etabliService = new EtabliService(this, etabliManager, customItemManager, rewardGiver, metierService, messages);
        Bukkit.getPluginManager().registerEvents(
                new EtabliListener(this, etabliManager, etabliService, customItemManager, messages), this);
        getCommand("etabli").setExecutor(new EtabliCommand(etabliConfig, etabliManager, messages));
    }

    @Override
    public void onDisable() {
        if (luckyBlockManager != null) {
            luckyBlockManager.unregisterRecipes();
        }
        if (generatorManager != null) {
            generatorManager.unregisterRecipes();
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
