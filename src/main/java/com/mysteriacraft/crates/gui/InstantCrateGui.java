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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Animation "instantanee" : la ou les recompenses sont donnees immediatement et affichees
 * dans un simple ecran de confirmation (pas de suspense, juste le resultat).
 */
public class InstantCrateGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] RESULT_SLOTS = {11, 12, 13, 14, 15};
    private static final int CLOSE_SLOT = 22;

    private final Crate crate;
    private final List<CrateReward> rewards;
    private final CrateService crateService;
    private final MessageManager messages;

    public InstantCrateGui(Player viewer, Crate crate, List<CrateReward> rewards, CrateService crateService, MessageManager messages) {
        super(viewer);
        this.crate = crate;
        this.rewards = rewards;
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

        // Centre le ou les resultats : 1 recompense -> slot du milieu ; plusieurs -> etalees autour.
        int count = Math.min(rewards.size(), RESULT_SLOTS.length);
        int startOffset = (RESULT_SLOTS.length - count) / 2;
        for (int i = 0; i < count; i++) {
            ItemStack resultIcon = appendLore(rewards.get(i).displayIcon().clone(), messages.raw("crates.gui-gagne"));
            inventory.setItem(RESULT_SLOTS[startOffset + i], resultIcon);
        }

        inventory.setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("crates.gui-fermer")).build());

        // La recompense est appliquee tout de suite : c'est une animation "instantanee".
        crateService.giveRewards(viewer, rewards);
        crateService.finishOpening(viewer.getUniqueId());

        return inventory;
    }

    private ItemStack appendLore(ItemStack item, String extraLine) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(MessageManager.color(extraLine));
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
