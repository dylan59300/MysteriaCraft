package com.mysteriacraft.voucher.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.voucher.VoucherDefinition;
import com.mysteriacraft.voucher.VoucherService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Demande confirmation avant de consommer/utiliser un Voucher (clic-droit en main, voir
 * VoucherListener). Meme layout que le menu de confirmation habituel : annuler/objet/confirmer. */
public class VoucherConfirmGui extends Menu {

    private static final int SIZE = 27;
    private static final int CANCEL_SLOT = 11;
    private static final int VOUCHER_SLOT = 13;
    private static final int CONFIRM_SLOT = 15;

    private final VoucherService service;
    private final VoucherDefinition definition;
    private final MessageManager messages;

    public VoucherConfirmGui(Player viewer, VoucherService service, VoucherDefinition definition, MessageManager messages) {
        super(viewer);
        this.service = service;
        this.definition = definition;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("voucher.confirmation-titre")));
        holder.setInventory(inventory);

        ItemStack voucherIcon = service.createItem(definition, 1);
        inventory.setItem(VOUCHER_SLOT, voucherIcon);
        inventory.setItem(CANCEL_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(messages.raw("voucher.confirmation-annuler"))
                .lore(List.of(messages.raw("voucher.confirmation-cliquer")))
                .build());
        inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(messages.raw("voucher.confirmation-confirmer"))
                .lore(List.of(messages.raw("voucher.confirmation-cliquer")))
                .build());
        return inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == CONFIRM_SLOT) {
            player.closeInventory();
            service.redeem(player, definition.id());
            return;
        }
        if (slot == CANCEL_SLOT) {
            player.closeInventory();
            messages.send(player, "voucher.confirmation-annulee");
        }
    }
}
