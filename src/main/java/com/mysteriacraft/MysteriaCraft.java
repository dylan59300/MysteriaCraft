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

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        // ---- Core ----
        this.configManager = new ConfigManager(this, "config.yml");
        this.messagesManager = new ConfigManager(this, "messages.yml");
        this.messages = new MessageManager(messagesManager, configManager);

        this.database = new Database(this, configManager.get().getString("base-de-donnees.fichier", "database.db"));
        database.connect();

        // ---- Module Economie ----
        setupEconomy();

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
