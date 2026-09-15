package com.mysteriacraft.banque;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestre les operations de banque : depot/retrait (transferes depuis/vers le solde principal,
 * module Economie), et consultation du solde (applique automatiquement les interets en attente).
 */
public class BanqueService {

    private final Plugin plugin;
    private final BanqueManager manager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public BanqueService(Plugin plugin, BanqueManager manager, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public void consulterSolde(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            BanqueManager.ResultatInterets resultat = manager.appliquerInteretsEnAttente(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (resultat.interetsGagnes() > 0.01) {
                    Map<String, String> interetPlaceholders = new HashMap<>();
                    interetPlaceholders.put("montant", economyManager.format(resultat.interetsGagnes()));
                    messages.send(player, "banque.interets-gagnes", interetPlaceholders);
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("solde", economyManager.format(resultat.nouveauSolde()));
                messages.send(player, "banque.solde", placeholders);
            });
        });
    }

    public void deposer(Player player, double montant) {
        if (montant < manager.getDepotMinimum()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("minimum", economyManager.format(manager.getDepotMinimum()));
            messages.send(player, "banque.depot-minimum", placeholders);
            return;
        }
        if (!economyManager.has(player.getUniqueId(), montant) || !economyManager.withdraw(player.getUniqueId(), montant)) {
            messages.send(player, "banque.fonds-insuffisants");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double nouveauSolde = manager.deposer(player.getUniqueId(), montant);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("montant", economyManager.format(montant));
                placeholders.put("solde", economyManager.format(nouveauSolde));
                messages.send(player, "banque.depot-reussi", placeholders);
            });
        });
    }

    public void retirer(Player player, double montant) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double nouveauSolde = manager.retirer(player.getUniqueId(), montant);
            if (nouveauSolde < 0) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "banque.solde-insuffisant"));
                return;
            }
            economyManager.deposit(player.getUniqueId(), montant);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("montant", economyManager.format(montant));
                placeholders.put("solde", economyManager.format(nouveauSolde));
                messages.send(player, "banque.retrait-reussi", placeholders);
            });
        });
    }
}
