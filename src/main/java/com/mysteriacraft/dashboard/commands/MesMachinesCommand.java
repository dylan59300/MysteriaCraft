package com.mysteriacraft.dashboard.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.generator.GeneratorManager;
import com.mysteriacraft.customitems.miningmachine.MiningMachineManager;
import com.mysteriacraft.dashboard.gui.MesMachinesGui;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.luckyblock.generator.LuckyBlockGeneratorManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /mesmachines : ouvre un menu recapitulatif de TOUS les blocs actifs possedes par le joueur
 * (Generateurs d'argent/ressources, Generateur de Lucky Block, Machine a Miner), pour ne pas avoir
 * a jongler entre les commandes /generateur liste, /generateurlb liste et /machineminiere.
 */
public class MesMachinesCommand implements CommandExecutor {

    private final GeneratorManager generatorManager;
    private final LuckyBlockGeneratorManager luckyBlockGeneratorManager;
    private final MiningMachineManager miningMachineManager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public MesMachinesCommand(GeneratorManager generatorManager, LuckyBlockGeneratorManager luckyBlockGeneratorManager,
                               MiningMachineManager miningMachineManager, EconomyManager economyManager,
                               MessageManager messages) {
        this.generatorManager = generatorManager;
        this.luckyBlockGeneratorManager = luckyBlockGeneratorManager;
        this.miningMachineManager = miningMachineManager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }
        new MesMachinesGui(player, generatorManager, luckyBlockGeneratorManager, miningMachineManager, economyManager, messages).open();
        return true;
    }
}
