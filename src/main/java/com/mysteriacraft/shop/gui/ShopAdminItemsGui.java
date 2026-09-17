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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Editeur des articles d'UNE categorie de la Boutique (voir ShopAdminCategoriesGui) : liste TOUS
 * les articles (meme hors-saison), permet d'en ajouter un a partir de l'objet tenu en main, ou de
 * supprimer la categorie entiere.
 */
public class ShopAdminItemsGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int RETOUR_SLOT = 45;
    private static final int ADD_SLOT = 49;
    private static final int SUPPRIMER_CATEGORIE_SLOT = 53;

    private final Plugin plugin;
    private final ShopManager manager;
    private final ShopEditorService editorService;
    private final String categoryId;
    private final MessageManager messages;

    private Inventory inventory;
    private final Map<Integer, String> slotToItemId = new HashMap<>();

    public ShopAdminItemsGui(Plugin plugin, Player viewer, ShopManager manager, ShopEditorService editorService,
                              String categoryId, MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.editorService = editorService;
        this.categoryId = categoryId;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        ShopManager.ShopCategory category = manager.getCategory(categoryId);
        String titre = replace(messages.raw("boutique.editeur-titre-articles"), "categorie",
                category != null ? category.displayName() : categoryId);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(titre));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        ShopManager.ShopCategory category = manager.getCategory(categoryId);
        if (category == null) {
            viewer.closeInventory();
            return;
        }
        inventory.clear();
        slotToItemId.clear();

        List<ShopManager.ShopItem> items = new ArrayList<>(category.items());
        for (int i = 0; i < CONTENT_SLOTS.length && i < items.size(); i++) {
            ShopManager.ShopItem item = items.get(i);
            List<String> lore = List.of(
                    replace(messages.raw("boutique.editeur-article-lore-achat"), "prix", String.valueOf(item.buyPrice())),
                    replace(messages.raw("boutique.editeur-article-lore-vente"), "prix", String.valueOf(item.sellPrice())),
                    messages.raw("boutique.editeur-article-lore-cliquer"));
            ItemStack icon = item.icon() != null ? item.icon().clone() : new ItemStack(Material.STONE);
            inventory.setItem(CONTENT_SLOTS[i], new ItemBuilder(icon.getType()).name(item.displayName()).lore(lore).build());
            slotToItemId.put(CONTENT_SLOTS[i], item.id());
        }

        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("boutique.editeur-retour")).build());
        inventory.setItem(ADD_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(messages.raw("boutique.editeur-ajouter-article"))
                .lore(List.of(messages.raw("boutique.editeur-ajouter-article-lore")))
                .build());
        inventory.setItem(SUPPRIMER_CATEGORIE_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("boutique.editeur-supprimer-categorie"))
                .lore(List.of(messages.raw("boutique.editeur-supprimer-categorie-lore")))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == RETOUR_SLOT) {
            new ShopAdminCategoriesGui(plugin, player, manager, editorService, messages).open();
            return;
        }
        if (slot == SUPPRIMER_CATEGORIE_SLOT) {
            manager.removeCategory(categoryId);
            new ShopAdminCategoriesGui(plugin, player, manager, editorService, messages).open();
            return;
        }
        if (slot == ADD_SLOT) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                messages.send(player, "boutique.editeur-main-vide");
                return;
            }
            ShopManager.ShopCategory category = manager.getCategory(categoryId);
            String baseId = held.getType().name().toLowerCase();
            String itemId = baseId;
            int suffixe = 2;
            while (manager.getItem(category, itemId) != null) {
                itemId = baseId + "_" + suffixe;
                suffixe++;
            }
            manager.addItem(categoryId, itemId, held, 0);
            render();
            return;
        }
        String itemId = slotToItemId.get(slot);
        if (itemId != null) {
            ShopManager.ShopCategory category = manager.getCategory(categoryId);
            ShopManager.ShopItem item = manager.getItem(category, itemId);
            if (item != null) {
                new ShopAdminItemEditorGui(plugin, player, manager, editorService, item, messages).open();
            }
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
