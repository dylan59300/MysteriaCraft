package com.mysteriacraft.shop.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.shop.ShopEditorService;
import com.mysteriacraft.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Edite un article precis de la Boutique (voir ShopAdminItemsGui) : prix d'achat/vente et stock
 * max (saisis au chat), ou suppression de l'article. La recompense elle-meme (type ITEM fixe a la
 * creation) n'est pas modifiable ici : supprimez et recreez l'article pour en changer.
 */
public class ShopAdminItemEditorGui extends Menu {

    private static final int SIZE = 27;
    private static final int PRIX_ACHAT_SLOT = 10;
    private static final int PRIX_VENTE_SLOT = 12;
    private static final int STOCK_MAX_SLOT = 14;
    private static final int SUPPRIMER_SLOT = 16;
    private static final int RETOUR_SLOT = 22;

    private final Plugin plugin;
    private final ShopManager manager;
    private final ShopEditorService editorService;
    private final String categoryId;
    private final String itemId;
    private final MessageManager messages;

    private Inventory inventory;

    public ShopAdminItemEditorGui(Plugin plugin, Player viewer, ShopManager manager, ShopEditorService editorService,
                                   ShopManager.ShopItem item, MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.editorService = editorService;
        this.categoryId = item.categoryId();
        this.itemId = item.id();
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("boutique.editeur-titre-article")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        ShopManager.ShopCategory category = manager.getCategory(categoryId);
        ShopManager.ShopItem item = manager.getItem(category, itemId);
        if (item == null) {
            viewer.closeInventory();
            return;
        }
        inventory.clear();

        inventory.setItem(PRIX_ACHAT_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name(replace(messages.raw("boutique.editeur-prix-achat-nom"), "prix", String.valueOf(item.buyPrice())))
                .lore(List.of(messages.raw("boutique.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(PRIX_VENTE_SLOT, new ItemBuilder(Material.EMERALD)
                .name(replace(messages.raw("boutique.editeur-prix-vente-nom"), "prix", String.valueOf(item.sellPrice())))
                .lore(List.of(messages.raw("boutique.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(STOCK_MAX_SLOT, new ItemBuilder(Material.CHEST)
                .name(replace(messages.raw("boutique.editeur-stock-max-nom"), "stock", item.hasStockLimite() ? String.valueOf(item.stockMax()) : "0"))
                .lore(List.of(messages.raw("boutique.editeur-stock-max-lore"), messages.raw("boutique.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(SUPPRIMER_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("boutique.editeur-supprimer-article"))
                .lore(List.of(messages.raw("boutique.editeur-supprimer-article-lore")))
                .build());
        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("boutique.editeur-retour")).build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == RETOUR_SLOT) {
            new ShopAdminItemsGui(plugin, player, manager, editorService, categoryId, messages).open();
            return;
        }
        if (slot == PRIX_ACHAT_SLOT) {
            editorService.requestItemField(player, categoryId, itemId, "prix-achat");
            return;
        }
        if (slot == PRIX_VENTE_SLOT) {
            editorService.requestItemField(player, categoryId, itemId, "prix-vente");
            return;
        }
        if (slot == STOCK_MAX_SLOT) {
            editorService.requestItemField(player, categoryId, itemId, "stock-max");
            return;
        }
        if (slot == SUPPRIMER_SLOT) {
            manager.removeItem(categoryId, itemId);
            new ShopAdminItemsGui(plugin, player, manager, editorService, categoryId, messages).open();
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
