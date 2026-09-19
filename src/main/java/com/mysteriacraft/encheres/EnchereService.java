package com.mysteriacraft.encheres;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestre l'Hotel des Ventes : mise en vente (retire l'item de l'inventaire, le serialise en
 * base), achat (paiement AVANT suppression de l'annonce, remboursement automatique si elle a
 * deja ete achetee entre-temps par quelqu'un d'autre) et annulation par le vendeur.
 */
public class EnchereService {

    private final Plugin plugin;
    private final EnchereManager manager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public EnchereService(Plugin plugin, EnchereManager manager, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public void vendre(Player player, double prix) {
        if (prix < manager.getPrixMinimum()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("minimum", economyManager.format(manager.getPrixMinimum()));
            messages.send(player, "encheres.prix-minimum", placeholders);
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            messages.send(player, "encheres.aucun-item");
            return;
        }
        ItemStack toList = item.clone();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int actives = manager.countAnnoncesActives(player.getUniqueId());
            if (actives >= manager.getMaxAnnoncesParJoueur()) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "encheres.limite-annonces"));
                return;
            }
            int id = manager.creerAnnonce(player.getUniqueId(), player.getName(), toList, prix);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (id < 0) {
                    messages.send(player, "encheres.erreur-creation");
                    return;
                }
                player.getInventory().setItemInMainHand(null);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("prix", economyManager.format(prix));
                messages.send(player, "encheres.mise-en-vente", placeholders);
            });
        });
    }

    public void acheter(Player buyer, int annonceId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Annonce annonce = manager.getAnnonce(annonceId);
            if (annonce == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(buyer, "encheres.deja-vendue"));
                return;
            }
            if (annonce.vendeurUuid().equals(buyer.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(buyer, "encheres.propre-annonce"));
                return;
            }
            if (!economyManager.has(buyer.getUniqueId(), annonce.prix()) || !economyManager.withdraw(buyer.getUniqueId(), annonce.prix())) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(buyer, "encheres.fonds-insuffisants"));
                return;
            }

            if (!manager.supprimerAnnonce(annonceId)) {
                // Achetee par quelqu'un d'autre entre-temps : remboursement immediat.
                economyManager.deposit(buyer.getUniqueId(), annonce.prix());
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(buyer, "encheres.deja-vendue"));
                return;
            }

            double commission = annonce.prix() * manager.getCommissionPourcent() / 100.0;
            economyManager.deposit(annonce.vendeurUuid(), annonce.prix() - commission);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(annonce.item());
                leftovers.values().forEach(leftover -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), leftover));

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("prix", economyManager.format(annonce.prix()));
                placeholders.put("vendeur", annonce.vendeurNom());
                messages.send(buyer, "encheres.achat-reussi", placeholders);

                Player vendeur = Bukkit.getPlayer(annonce.vendeurUuid());
                if (vendeur != null) {
                    Map<String, String> vendeurPlaceholders = new HashMap<>();
                    vendeurPlaceholders.put("prix", economyManager.format(annonce.prix() - commission));
                    vendeurPlaceholders.put("acheteur", buyer.getName());
                    messages.send(vendeur, "encheres.vente-effectuee", vendeurPlaceholders);
                }
            });
        });
    }

    public void annuler(Player player, int annonceId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Annonce annonce = manager.getAnnonce(annonceId);
            if (annonce == null || !annonce.vendeurUuid().equals(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "encheres.annonce-introuvable"));
                return;
            }
            if (!manager.supprimerAnnonce(annonceId)) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "encheres.deja-vendue"));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(annonce.item());
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                messages.send(player, "encheres.annonce-annulee");
            });
        });
    }
}
