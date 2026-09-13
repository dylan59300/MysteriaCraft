package com.mysteriacraft.crates.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.crates.Crate;
import com.mysteriacraft.crates.CrateReward;
import com.mysteriacraft.crates.CrateService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Animation "instantanee" : la recompense est donnee immediatement et affichee dans un
 * simple ecran de confirmation (pas de suspense, juste le resultat).
 */
public class InstantCrateGui extends Menu {

    private static final int SIZE = 27;
    private static final int RESULT_SLOT = 13;
    private static final int CLOSE_SLOT = 22;

    private final Crate crate;
    private final CrateReward reward;
    private final CrateService crateService;
    private final MessageManager messages;

    public InstantCrateGui(Player viewer, Crate crate, CrateReward reward, CrateService crateService, MessageManager messages) {
        super(viewer);
        this.crate = crate;
        this.reward = reward;
        this.crateService = crateService;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        String title = MessageManager.color(crate.displayName());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(messages.raw("crates.gui-gagne"));
        ItemStack resultIcon = reward.displayIcon().clone();
        inventory.setItem(RESULT_SLOT, appendLore(resultIcon, lore));

        inventory.setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("crates.gui-fermer")).build());

        // La recompense est appliquee tout de suite : c'est une animation "instantanee".
        crateService.giveReward(viewer, reward);

        return inventory;
    }

    private ItemStack appendLore(ItemStack item, List<String> extraLore) {
        var meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            for (String line : extraLore) {
                lore.add(MessageManager.color(line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            player.closeInventory();
        }
    }
}
