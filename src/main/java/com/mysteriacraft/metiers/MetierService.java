package com.mysteriacraft.metiers;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Traite le choix de metier et la distribution d'xp/bonus lies (peche pour le Pecheur, potions
 * pour l'Alchimiste ; le Forgeron est recompense directement par EtabliService). */
public class MetierService {

    private final Plugin plugin;
    private final MetierManager manager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public MetierService(Plugin plugin, MetierManager manager, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public void choisir(Player player, String metierId) {
        MetierManager.MetierDefinition metier = manager.getMetier(metierId);
        if (metier == null) {
            messages.send(player, "metier.introuvable");
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            manager.choisir(uuid, metier.id());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("metier", metier.nom());
                messages.send(player, "metier.choisi", placeholders);
            });
        });
    }

    public void showInfo(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String metierId = manager.getMetierId(uuid);
            MetierManager.MetierDefinition metier = manager.getMetier(metierId);
            int niveau = manager.getNiveau(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (metier == null) {
                    messages.send(player, "metier.aucun");
                    return;
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("metier", metier.nom());
                placeholders.put("niveau", String.valueOf(niveau));
                messages.send(player, "metier.info", placeholders);
            });
        });
    }

    /** A appeler par un listener quand un joueur Pecheur attrape un poisson (vanilla) : chance
     * scalant avec son niveau de gagner un petit bonus d'argent, plus l'xp de peche. Appel
     * hors du thread principal recommande (fait des lectures SQLite). */
    public void onCatchFish(Player player) {
        UUID uuid = player.getUniqueId();
        if (!"pecheur".equalsIgnoreCase(manager.getMetierId(uuid))) {
            return;
        }
        manager.addXp(uuid, 5);
        double bonus = manager.getBonus(uuid);
        if (bonus > 0 && ThreadLocalRandom.current().nextDouble(100.0) < bonus) {
            economyManager.deposit(uuid, bonus);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("montant", economyManager.format(bonus));
                    messages.send(player, "metier.pecheur-bonus", placeholders);
                }
            });
        }
    }

    /** A appeler par un listener quand un joueur Alchimiste consomme une potion : chance scalant
     * avec son niveau de lui rendre un exemplaire, plus l'xp d'alchimie. Renvoie true si la
     * potion doit etre rendue. Hors du thread principal recommande. */
    public boolean onDrinkPotion(Player player) {
        UUID uuid = player.getUniqueId();
        if (!"alchimiste".equalsIgnoreCase(manager.getMetierId(uuid))) {
            return false;
        }
        manager.addXp(uuid, 5);
        double bonus = manager.getBonus(uuid);
        return bonus > 0 && ThreadLocalRandom.current().nextDouble(100.0) < bonus;
    }

    /** A appeler par EtabliService quand un joueur Forgeron reussit un craft : chance scalant
     * avec son niveau de ne pas consommer un ingredient, plus l'xp de forge. Hors du thread
     * principal recommande. */
    public boolean onCraftEtabli(Player player) {
        UUID uuid = player.getUniqueId();
        if (!"forgeron".equalsIgnoreCase(manager.getMetierId(uuid))) {
            return false;
        }
        manager.addXp(uuid, 8);
        double bonus = manager.getBonus(uuid);
        return bonus > 0 && ThreadLocalRandom.current().nextDouble(100.0) < bonus;
    }
}
