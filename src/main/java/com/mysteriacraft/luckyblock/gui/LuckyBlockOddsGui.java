package com.mysteriacraft.luckyblock.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.luckyblock.LuckyBlockEffect;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Menu en lecture seule montrant, pour une famille de Lucky Block, sa chance de base de BON,
 * puis le detail des effets BON (haut) et MAUVAIS (bas) avec leur % au sein de leur pool.
 */
public class LuckyBlockOddsGui extends Menu {

    private static final int SIZE = 27;
    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 22;
    private static final int[] GOOD_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] BAD_SLOTS = {19, 20, 21, 23, 24, 25};
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.0");

    private final LuckyBlockFamily family;
    private final LuckyBlockGui parent;
    private final MessageManager messages;

    public LuckyBlockOddsGui(Player viewer, LuckyBlockFamily family, LuckyBlockGui parent, MessageManager messages) {
        super(viewer);
        this.family = family;
        this.parent = parent;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        String title = MessageManager.color(messages.raw("luckyblock.gui-odds-titre")) + " : " + MessageManager.color(family.displayName());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        List<String> infoLore = new ArrayList<>();
        infoLore.add(messages.raw("luckyblock.gui-odds-info-base")
                .replace("{chance}", String.valueOf((int) family.baseGoodChance())));
        infoLore.add(messages.raw("luckyblock.gui-odds-info-bonus"));
        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.NETHER_STAR)
                .name(messages.raw("luckyblock.gui-odds-info-titre")).lore(infoLore).build());

        placePool(inventory, family.goodEffects(), GOOD_SLOTS, true);
        placePool(inventory, family.badEffects(), BAD_SLOTS, false);

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name(messages.raw("general.gui-retour")).build());

        return inventory;
    }

    private void placePool(Inventory inventory, List<LuckyBlockEffect> pool, int[] slots, boolean good) {
        double totalWeight = 0;
        for (LuckyBlockEffect effect : pool) {
            totalWeight += effect.chance();
        }
        for (int i = 0; i < slots.length && i < pool.size(); i++) {
            LuckyBlockEffect effect = pool.get(i);
            double percent = totalWeight > 0 ? (effect.chance() / totalWeight) * 100.0 : 0.0;

            List<String> lore = new ArrayList<>();
            lore.add(messages.raw(good ? "luckyblock.gui-odds-tag-bon" : "luckyblock.gui-odds-tag-mauvais"));
            lore.add(messages.raw("luckyblock.gui-odds-pourcentage").replace("{pourcentage}", PERCENT_FORMAT.format(percent)));

            Material icon = good ? Material.LIME_DYE : Material.RED_DYE;
            inventory.setItem(slots[i], new ItemBuilder(icon).name(effect.displayName()).lore(lore).build());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == BACK_SLOT && event.getWhoClicked() instanceof Player) {
            parent.open();
        }
        // Menu en lecture seule sinon : les autres clics sont ignores.
    }
}
