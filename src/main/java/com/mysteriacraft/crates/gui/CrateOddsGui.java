package com.mysteriacraft.crates.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.crates.Crate;
import com.mysteriacraft.crates.CrateReward;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Menu en lecture seule affichant la table de loot d'une caisse avec le pourcentage de chance
 * reel de chaque recompense (calcule a partir des poids relatifs de crates.yml).
 */
public class CrateOddsGui extends Menu {

    private static final int SIZE = 27;
    private static final int BACK_SLOT = 22;
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.00");

    private final Crate crate;
    private final CrateListGui parent;
    private final MessageManager messages;

    public CrateOddsGui(Player viewer, Crate crate, CrateListGui parent, MessageManager messages) {
        super(viewer);
        this.crate = crate;
        this.parent = parent;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        String title = MessageManager.color(messages.raw("crates.gui-odds-titre")) + " : " + MessageManager.color(crate.displayName());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        double totalWeight = crate.totalWeight();
        int slot = 9;
        for (CrateReward reward : crate.rewards()) {
            if (slot >= SIZE) {
                break; // Table de loot avec plus d'entrees que le menu ne peut en montrer d'un coup.
            }
            if (slot == BACK_SLOT) {
                slot++;
            }
            double percent = totalWeight > 0 ? (reward.chance() / totalWeight) * 100.0 : 0.0;
            inventory.setItem(slot, buildOddsItem(reward, percent));
            slot++;
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name(messages.raw("kits.gui-apercu-retour")).build());

        return inventory;
    }

    private ItemStack buildOddsItem(CrateReward reward, double percent) {
        ItemStack icon = reward.displayIcon().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(MessageManager.color(reward.rarity().color() + reward.rarity().name()));
            lore.add(MessageManager.color("&7Chance : &e" + PERCENT_FORMAT.format(percent) + "%"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == BACK_SLOT && event.getWhoClicked() instanceof Player) {
            parent.open();
        }
        // Menu en lecture seule : tous les autres clics sont ignores.
    }
}
