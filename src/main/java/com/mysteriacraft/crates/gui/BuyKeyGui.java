package com.mysteriacraft.crates.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.crates.Crate;
import com.mysteriacraft.crates.CrateService;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu d'achat de cles virtuelles pour une caisse, contre la monnaie interne.
 * Ouvert par un shift-clic sur une caisse dans CrateListGui.
 */
public class BuyKeyGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] OFFER_SLOTS = {11, 13, 15};
    private static final int[] QUANTITIES = {1, 5, 10};
    private static final int BACK_SLOT = 22;

    private final Crate crate;
    private final CrateListGui parent;
    private final CrateService crateService;
    private final EconomyManager economyManager;
    private final MessageManager messages;
    private final Map<Integer, Integer> slotToQuantity = new HashMap<>();

    public BuyKeyGui(Player viewer, Crate crate, CrateListGui parent, CrateService crateService,
                      EconomyManager economyManager, MessageManager messages) {
        super(viewer);
        this.crate = crate;
        this.parent = parent;
        this.crateService = crateService;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        String title = MessageManager.color(messages.raw("crates.gui-achat-titre")) + " : " + MessageManager.color(crate.displayName());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        for (int i = 0; i < QUANTITIES.length; i++) {
            int quantity = QUANTITIES[i];
            double price = crate.keyPrice() * quantity;

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("quantite", String.valueOf(quantity));
            placeholders.put("prix", economyManager.format(price));
            String name = messages.raw("crates.gui-achat-offre");
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                name = name.replace("{" + entry.getKey() + "}", entry.getValue());
            }

            inventory.setItem(OFFER_SLOTS[i], new ItemBuilder(Material.TRIPWIRE_HOOK, quantity)
                    .name(name)
                    .lore(List.of(messages.raw("crates.gui-achat-cliquer")))
                    .build());
            slotToQuantity.put(OFFER_SLOTS[i], quantity);
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name(messages.raw("kits.gui-apercu-retour")).build());

        return inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == BACK_SLOT) {
            parent.open();
            return;
        }
        Integer quantity = slotToQuantity.get(slot);
        if (quantity == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        crateService.buyKeys(player, crate, quantity);
        parent.refresh();
    }
}
