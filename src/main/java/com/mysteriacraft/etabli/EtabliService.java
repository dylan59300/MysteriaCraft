package com.mysteriacraft.etabli;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.metiers.MetierService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Traite la decouverte de recette (parchemin consomme sur le bloc) et le craft d'une recette deja
 * debloquee. Le metier Forgeron (voir MetierService#onCraftEtabli) a une chance de ne pas
 * consommer un ingredient au hasard.
 */
public class EtabliService {

    private final Plugin plugin;
    private final EtabliManager manager;
    private final CustomItemManager customItemManager;
    private final RewardGiver rewardGiver;
    private final MetierService metierService;
    private final MessageManager messages;

    public EtabliService(Plugin plugin, EtabliManager manager, CustomItemManager customItemManager,
                          RewardGiver rewardGiver, MetierService metierService, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.rewardGiver = rewardGiver;
        this.metierService = metierService;
        this.messages = messages;
    }

    public void deverrouiller(Player player) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String customId = customItemManager.getCustomItemId(inHand);
        EtabliManager.Recette recette = manager.getRecetteByParchemin(customId);
        if (recette == null) {
            messages.send(player, "etabli.parchemin-invalide");
            return;
        }

        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean deja = manager.isUnlocked(uuid, recette.id());
            if (!deja) {
                manager.unlock(uuid, recette.id());
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (deja) {
                    messages.send(player, "etabli.deja-debloquee");
                    return;
                }
                int remaining = inHand.getAmount() - 1;
                player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("recette", recette.nom());
                messages.send(player, "etabli.decouverte", placeholders);
            });
        });
    }

    public void craft(Player player, String recetteId) {
        EtabliManager.Recette recette = manager.getRecette(recetteId);
        if (recette == null) {
            messages.send(player, "etabli.recette-introuvable");
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean unlocked = manager.isUnlocked(uuid, recette.id());
            boolean skipOneIngredient = unlocked && metierService.onCraftEtabli(player);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!unlocked) {
                    messages.send(player, "etabli.non-debloquee");
                    return;
                }
                if (!hasIngredients(player, recette)) {
                    messages.send(player, "etabli.ingredients-manquants");
                    return;
                }
                removeIngredients(player, recette, skipOneIngredient);
                rewardGiver.give(player, recette.resultat());

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("recette", recette.nom());
                messages.send(player, skipOneIngredient ? "etabli.craft-reussi-forgeron" : "etabli.craft-reussi", placeholders);
            });
        });
    }

    private boolean hasIngredients(Player player, EtabliManager.Recette recette) {
        for (EtabliManager.Ingredient ingredient : recette.ingredients()) {
            if (countMatching(player, ingredient) < ingredient.quantite()) {
                return false;
            }
        }
        return true;
    }

    private int countMatching(Player player, EtabliManager.Ingredient ingredient) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && matches(item, ingredient)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private boolean matches(ItemStack item, EtabliManager.Ingredient ingredient) {
        if (ingredient.customItemId() != null) {
            return ingredient.customItemId().equalsIgnoreCase(customItemManager.getCustomItemId(item));
        }
        return item.getType() == ingredient.materiel();
    }

    private void removeIngredients(Player player, EtabliManager.Recette recette, boolean skipOne) {
        List<EtabliManager.Ingredient> ingredients = new ArrayList<>(recette.ingredients());
        if (skipOne && !ingredients.isEmpty()) {
            ingredients.remove(ThreadLocalRandom.current().nextInt(ingredients.size()));
        }
        for (EtabliManager.Ingredient ingredient : ingredients) {
            removeMatching(player, ingredient);
        }
    }

    private void removeMatching(Player player, EtabliManager.Ingredient ingredient) {
        int remaining = ingredient.quantite();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !matches(item, ingredient)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            contents[i] = item.getAmount() <= 0 ? null : item;
        }
        player.getInventory().setStorageContents(contents);
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
