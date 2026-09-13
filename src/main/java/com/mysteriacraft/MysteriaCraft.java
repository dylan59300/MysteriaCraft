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
        this.crateService = new CrateService(this, crateManager, economyManager, messages);

        getCommand("crate").setExecutor(new CrateCommand(this, crateManager, crateService, messages));
        getCommand("cratekey").setExecutor(new CrateKeyCommand(this, crateManager, messages));
        getCommand("cratereload").setExecutor(new CrateReloadCommand(cratesConfig, crateManager, messages));
    }

    @Override
    public void onDisable() {
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
