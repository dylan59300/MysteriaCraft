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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Editeur de categories de la Boutique en jeu (voir /boutique editeur), comme l'editeur de paliers
 * du BattlePass : liste TOUTES les categories (meme hors-saison) et permet d'en ajouter/supprimer
 * sans toucher a boutique.yml a la main.
 */
public class ShopAdminCategoriesGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int PREVIOUS_SLOT = 45;
    private static final int ADD_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final Plugin plugin;
    private final ShopManager manager;
    private final ShopEditorService editorService;
    private final MessageManager messages;

    private Inventory inventory;
    private int page = 0;
    private final Map<Integer, String> slotToCategoryId = new HashMap<>();

    public ShopAdminCategoriesGui(Plugin plugin, Player viewer, ShopManager manager,
                                   ShopEditorService editorService, MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.editorService = editorService;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("boutique.editeur-titre-categories")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        inventory.clear();
        slotToCategoryId.clear();

        List<ShopManager.ShopCategory> categories = new ArrayList<>(manager.getCategoriesSorted());
        int start = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int index = start + i;
            if (index >= categories.size()) {
                break;
            }
            ShopManager.ShopCategory category = categories.get(index);
            List<String> lore = List.of(
                    replace(messages.raw("boutique.editeur-categorie-lore-articles"), "nombre", String.valueOf(category.items().size())),
                    messages.raw("boutique.editeur-categorie-lore-cliquer"));
            inventory.setItem(CONTENT_SLOTS[i], new ItemBuilder(category.icon()).name(category.displayName()).lore(lore).build());
            slotToCategoryId.put(CONTENT_SLOTS[i], category.id());
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("general.gui-page-precedente")).build());
        }
        if (start + CONTENT_SLOTS.length < categories.size()) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("general.gui-page-suivante")).build());
        }
        inventory.setItem(ADD_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(messages.raw("boutique.editeur-ajouter-categorie"))
                .lore(List.of(messages.raw("boutique.editeur-ajouter-categorie-lore")))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == ADD_SLOT) {
            editorService.requestCategoryName(player);
            return;
        }
        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == NEXT_SLOT) {
            page++;
            render();
            return;
        }
        String categoryId = slotToCategoryId.get(slot);
        if (categoryId != null) {
            new ShopAdminItemsGui(plugin, player, manager, editorService, categoryId, messages).open();
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
