package com.mysteriacraft.classes;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestre le choix de classe/metier et l'application de son effet passif permanent.
 * Le premier choix est gratuit, en changer ensuite coute "prix-changement".
 */
public class ClasseService {

    /** Duree tres longue (environ 68 minutes) reappliquee periodiquement (voir ClasseListener),
     * plutot qu'une duree infinie non supportee proprement par toutes les versions de l'API. */
    private static final int EFFECT_DURATION_TICKS = 100_000;

    private final Plugin plugin;
    private final ClasseManager manager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public ClasseService(Plugin plugin, ClasseManager manager, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public void choisir(Player player, String classeId) {
        ClasseDefinition classe = manager.getClasse(classeId);
        if (classe == null) {
            messages.send(player, "classes.introuvable");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String actuelle = manager.getClasseActive(player.getUniqueId());
            if (classe.id().equalsIgnoreCase(actuelle)) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "classes.deja-active"));
                return;
            }

            boolean premierChoix = actuelle == null;
            if (!premierChoix) {
                double prix = manager.getPrixChangement();
                if (!economyManager.has(player.getUniqueId(), prix) || !economyManager.withdraw(player.getUniqueId(), prix)) {
                    Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "classes.fonds-insuffisants"));
                    return;
                }
            }

            manager.setClasseActive(player.getUniqueId(), classe.id());
            Bukkit.getScheduler().runTask(plugin, () -> {
                applyEffect(player, classe);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("classe", classe.displayName());
                messages.send(player, "classes.choisie", placeholders);
            });
        });
    }

    /** Applique (ou reapplique) l'effet de la classe active. Appele au choix, a la connexion et
     * periodiquement (voir ClasseListener) pour survivre a une mort (qui efface les effets). */
    public void applyEffect(Player player, ClasseDefinition classe) {
        player.addPotionEffect(new PotionEffect(classe.effet(), EFFECT_DURATION_TICKS, classe.amplificateur(), true, false));
    }

    public void applyActiveClassEffect(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String classeId = manager.getClasseActive(player.getUniqueId());
            ClasseDefinition classe = manager.getClasse(classeId);
            if (classe != null) {
                Bukkit.getScheduler().runTask(plugin, () -> applyEffect(player, classe));
            }
        });
    }

    /** A appeler periodiquement (voir MysteriaCraft) : reapplique l'effet de chaque joueur en
     * ligne, pour survivre a l'expiration de EFFECT_DURATION_TICKS sur une longue session. */
    public void reapplyAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyActiveClassEffect(player);
        }
    }
}
