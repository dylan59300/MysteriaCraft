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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu listant les articles d'une categorie de boutique. Clic gauche = acheter, clic droit =
 * revendre (si l'article tenu en main correspond, voir ShopService#sell).
 */
public class ShopItemsGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int BACK_SLOT = 18;
    private static final int PREVIOUS_SLOT = 19;
    private static final int NEXT_SLOT = 25;

    private final ShopManager.ShopCategory category;
    private final ShopManager manager;
    private final ShopService service;
    private final EconomyManager economyManager;
    private final MessageManager messages;
    private final Map<Integer, String> slotToItemId = new HashMap<>();

    private Inventory inventory;
    private int page = 0;

    public ShopItemsGui(Player viewer, ShopManager.ShopCategory category, ShopManager manager,
                         ShopService service, EconomyManager economyManager, MessageManager messages) {
        super(viewer);
        this.category = category;
        this.manager = manager;
        this.service = service;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(category.displayName()));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(category.items().size() / (double) ITEM_SLOTS.length));
    }

    private void render() {
        slotToItemId.clear();

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        List<ShopManager.ShopItem> items = category.items();
        int firstIndex = page * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= items.size()) {
                break;
            }
            ShopManager.ShopItem item = items.get(index);
            inventory.setItem(ITEM_SLOTS[i], buildItemIcon(item));
            slotToItemId.put(ITEM_SLOTS[i], item.id());
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("boutique.gui-retour")).build());

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

    private ItemStack buildItemIcon(ShopManager.ShopItem item) {
        ItemStack icon = item.icon() != null ? item.icon().clone() : new ItemStack(Material.STONE);
        List<String> lore = new ArrayList<>();
        if (item.isPurchasable()) {
            lore.add(replace(messages.raw("boutique.gui-prix-achat"), "prix", economyManager.format(item.buyPrice())));
        } else {
            lore.add(messages.raw("boutique.gui-non-achetable"));
        }
        if (item.isSellable()) {
            lore.add(replace(messages.raw("boutique.gui-prix-vente"), "prix", economyManager.format(item.sellPrice())));
        }
        lore.add("");
        if (item.isPurchasable()) {
            lore.add(messages.raw("boutique.gui-clic-acheter"));
        }
        if (item.isSellable()) {
            lore.add(messages.raw("boutique.gui-clic-vendre"));
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> combined = new ArrayList<>(meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of());
            combined.add("");
            combined.addAll(lore);
            meta.setLore(combined);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == BACK_SLOT) {
            if (event.getWhoClicked() instanceof Player player) {
                new ShopCategoriesGui(player, manager, service, economyManager, messages).open();
            }
            return;
        }
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

        String itemId = slotToItemId.get(slot);
        if (itemId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ShopManager.ShopItem item = manager.getItem(category, itemId);
        if (item == null) {
            return;
        }

        if (event.getClick() == ClickType.RIGHT) {
            service.sell(player, item);
        } else {
            service.buy(player, item);
        }
    }
}
