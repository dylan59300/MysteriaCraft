package com.mysteriacraft.customitems.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.customitems.CustomItemDefinition;
import com.mysteriacraft.customitems.CustomItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalogue en lecture seule (/customitem) listant TOUS les items custom disponibles (minerais,
 * carburants, kits, equipement "full custom"...), avec leur apparence reelle et une ligne
 * indiquant comment se les procurer (minage, craft, ou recompense uniquement).
 */
public class CustomItemsGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PREVIOUS_SLOT = 48;
    private static final int NEXT_SLOT = 50;

    private final CustomItemManager manager;
    private final MessageManager messages;

    private Inventory inventory;
    private List<CustomItemDefinition> items;
    private int page = 0;

    public CustomItemsGui(Player viewer, CustomItemManager manager, MessageManager messages) {
        super(viewer);
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("customitem.gui-titre")));
        holder.setInventory(inventory);
        this.items = new ArrayList<>(manager.getItemsSorted());
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(items.size() / (double) ITEM_SLOTS.length));
    }

    private void render() {
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int firstIndex = page * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= items.size()) {
                break;
            }
            inventory.setItem(ITEM_SLOTS[i], buildIcon(items.get(index)));
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

    /** Icone = l'item reel (meme apparence/enchantements que celui obtenu en jeu), avec une ligne
     * supplementaire indiquant comment se le procurer et son id (pour /customitem give/sell). */
    private ItemStack buildIcon(CustomItemDefinition definition) {
        ItemStack item = manager.createItem(definition);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<String> lore = new ArrayList<>(meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of());
        lore.add("");
        if (definition.hasNaturalSource()) {
            lore.add(messages.raw("customitem.gui-source-minage"));
        }
        if (definition.isCraftable()) {
            lore.add(messages.raw("customitem.gui-source-craft"));
        }
        if (!definition.hasNaturalSource() && !definition.isCraftable()) {
            lore.add(messages.raw("customitem.gui-source-recompense"));
        }
        if (definition.isSellable()) {
            lore.add(replace(messages.raw("customitem.gui-prix-vente"), "prix", String.valueOf(definition.sellPrice())));
        }
        lore.add(replace(messages.raw("customitem.gui-id"), "id", definition.id()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            render();
        } else if (slot == NEXT_SLOT && page < maxPage() - 1) {
            page++;
            render();
        }
        // Catalogue en lecture seule : aucune autre action au clic.
    }
}
