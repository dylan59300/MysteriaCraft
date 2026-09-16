package com.mysteriacraft.shop.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.shop.ShopManager;
import com.mysteriacraft.shop.ShopService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menu listant les categories de la Boutique. Clic sur une categorie ouvre ses articles. */
public class ShopCategoriesGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PREVIOUS_SLOT = 18;
    private static final int NEXT_SLOT = 26;

    private final ShopManager manager;
    private final ShopService service;
    private final EconomyManager economyManager;
    private final MessageManager messages;
    private final Map<Integer, String> slotToCategoryId = new HashMap<>();

    private Inventory inventory;
    private List<ShopManager.ShopCategory> categories;
    private int page = 0;

    public ShopCategoriesGui(Player viewer, ShopManager manager, ShopService service,
                              EconomyManager economyManager, MessageManager messages) {
        super(viewer);
        this.manager = manager;
        this.service = service;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("boutique.titre-categories")));
        holder.setInventory(inventory);
        this.categories = new ArrayList<>(manager.getCategoriesSorted());
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(categories.size() / (double) CATEGORY_SLOTS.length));
    }

    private void render() {
        slotToCategoryId.clear();

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int firstIndex = page * CATEGORY_SLOTS.length;
        for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= categories.size()) {
                break;
            }
            ShopManager.ShopCategory category = categories.get(index);
            List<String> lore = List.of(
                    messages.raw("boutique.gui-nombre-articles").replace("{nombre}", String.valueOf(category.items().size())),
                    messages.raw("boutique.gui-clic-ouvrir")
            );
            inventory.setItem(CATEGORY_SLOTS[i], new ItemBuilder(category.icon()).name(category.displayName()).lore(lore).build());
            slotToCategoryId.put(CATEGORY_SLOTS[i], category.id());
        }

        int maxPage = maxPage();
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW)
                    .name(messages.raw("general.gui-page-precedente")).build());
        }
        if (page < maxPage - 1) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW)
                    .name(messages.raw("general.gui-page-suivante")).build());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == NEXT_SLOT && page < maxPage() - 1) {
            page++;
            render();
            return;
        }

        String categoryId = slotToCategoryId.get(slot);
        if (categoryId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ShopManager.ShopCategory category = manager.getCategory(categoryId);
        if (category == null) {
            return;
        }
        new ShopItemsGui(player, category, manager, service, economyManager, messages).open();
    }
}
