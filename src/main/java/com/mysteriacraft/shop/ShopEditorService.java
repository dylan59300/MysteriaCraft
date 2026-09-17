package com.mysteriacraft.shop;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.shop.gui.ShopAdminCategoriesGui;
import com.mysteriacraft.shop.gui.ShopAdminItemEditorGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gere la saisie au clavier (via le chat) utilisee par l'editeur de categories/articles de la
 * Boutique en jeu (voir ShopAdminCategoriesGui/ShopAdminItemsGui/ShopAdminItemEditorGui et
 * /boutique editeur), sur le meme principe que BattlePassEditorService.
 */
public class ShopEditorService {

    private record PendingItemField(String categoryId, String itemId, String champ) {
    }

    private final Plugin plugin;
    private final ShopManager manager;
    private final MessageManager messages;

    private final Map<UUID, Boolean> pendingCategoryName = new ConcurrentHashMap<>();
    private final Map<UUID, PendingItemField> pendingItemField = new ConcurrentHashMap<>();

    public ShopEditorService(Plugin plugin, ShopManager manager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    public void requestCategoryName(Player player) {
        pendingCategoryName.put(player.getUniqueId(), true);
        player.closeInventory();
        messages.send(player, "boutique.editeur-saisir-nom-categorie");
    }

    public void requestItemField(Player player, String categoryId, String itemId, String champ) {
        pendingItemField.put(player.getUniqueId(), new PendingItemField(categoryId, itemId, champ));
        player.closeInventory();
        messages.send(player, "boutique.editeur-saisir-" + champ);
    }

    public boolean hasPendingInput(UUID uuid) {
        return pendingCategoryName.containsKey(uuid) || pendingItemField.containsKey(uuid);
    }

    /** Appele par ShopEditorChatListener (deja sur le thread principal) avec le message tape. */
    public void handleChatInput(Player player, String message) {
        UUID uuid = player.getUniqueId();

        if (pendingCategoryName.remove(uuid) != null) {
            String nom = message.trim();
            String id = nom.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if (id.isEmpty()) {
                messages.send(player, "boutique.editeur-valeur-invalide");
                return;
            }
            ItemStack main = player.getInventory().getItemInMainHand();
            Material icone = main != null && !main.getType().isAir() ? main.getType() : Material.CHEST;
            manager.addCategory(id, nom, icone);
            messages.send(player, "boutique.editeur-categorie-creee");
            Bukkit.getScheduler().runTask(plugin, () -> new ShopAdminCategoriesGui(plugin, player, manager, this, messages).open());
            return;
        }

        PendingItemField pending = pendingItemField.remove(uuid);
        if (pending == null) {
            return;
        }

        if (pending.champ().equals("actif-du") || pending.champ().equals("actif-au")) {
            handleDateInput(player, pending, message.trim());
            return;
        }

        double valeur;
        try {
            valeur = Double.parseDouble(message.trim());
        } catch (NumberFormatException e) {
            messages.send(player, "boutique.editeur-valeur-invalide");
            reopenItemEditor(player, pending);
            return;
        }
        switch (pending.champ()) {
            case "prix-achat" -> manager.setItemPrixAchat(pending.categoryId(), pending.itemId(), valeur);
            case "prix-vente" -> manager.setItemPrixVente(pending.categoryId(), pending.itemId(), valeur);
            case "stock-max" -> manager.setItemStockMax(pending.categoryId(), pending.itemId(), (int) valeur);
            case "reappro-intervalle-minutes" -> manager.setItemReapproIntervalle(pending.categoryId(), pending.itemId(), (int) valeur);
            case "reappro-quantite" -> manager.setItemReapproQuantite(pending.categoryId(), pending.itemId(), (int) valeur);
            default -> {
            }
        }
        messages.send(player, "boutique.editeur-valeur-modifiee");
        reopenItemEditor(player, pending);
    }

    private static final java.util.regex.Pattern DATE_MM_JJ = java.util.regex.Pattern.compile(
            "^(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$");

    /** Saisie d'une date "MM-jj" (voir "editions saisonnieres") ; "aucun"/"aucune" efface la borne. */
    private void handleDateInput(Player player, PendingItemField pending, String valeur) {
        boolean effacer = valeur.equalsIgnoreCase("aucun") || valeur.equalsIgnoreCase("aucune");
        if (!effacer && !DATE_MM_JJ.matcher(valeur).matches()) {
            messages.send(player, "boutique.editeur-date-invalide");
            reopenItemEditor(player, pending);
            return;
        }
        String valeurFinale = effacer ? null : valeur;
        if (pending.champ().equals("actif-du")) {
            manager.setItemActifDu(pending.categoryId(), pending.itemId(), valeurFinale);
        } else {
            manager.setItemActifAu(pending.categoryId(), pending.itemId(), valeurFinale);
        }
        messages.send(player, "boutique.editeur-valeur-modifiee");
        reopenItemEditor(player, pending);
    }

    private void reopenItemEditor(Player player, PendingItemField pending) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ShopManager.ShopCategory category = manager.getCategory(pending.categoryId());
            ShopManager.ShopItem item = manager.getItem(category, pending.itemId());
            if (item != null) {
                new ShopAdminItemEditorGui(plugin, player, manager, this, item, messages).open();
            }
        });
    }
}
