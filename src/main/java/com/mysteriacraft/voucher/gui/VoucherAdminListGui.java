package com.mysteriacraft.voucher.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.voucher.VoucherDefinition;
import com.mysteriacraft.voucher.VoucherEditorService;
import com.mysteriacraft.voucher.VoucherManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Liste (triee par nom, paginee) tous les types de Voucher : /voucher admin. Permet d'en creer un
 * a partir de l'objet tenu en main, de recharger voucher.yml, ou d'en ouvrir un pour l'editer. */
public class VoucherAdminListGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int PREVIOUS_SLOT = 45;
    private static final int CREATE_SLOT = 49;
    private static final int RELOAD_SLOT = 48;
    private static final int NEXT_SLOT = 53;

    private final VoucherManager manager;
    private final VoucherEditorService editorService;
    private final MessageManager messages;

    private Inventory inventory;
    private int page = 0;
    private final Map<Integer, String> slotToVoucherId = new HashMap<>();

    public VoucherAdminListGui(Player viewer, VoucherManager manager, VoucherEditorService editorService, MessageManager messages) {
        super(viewer);
        this.manager = manager;
        this.editorService = editorService;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("voucher.editeur-titre-liste")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        inventory.clear();
        slotToVoucherId.clear();

        List<VoucherDefinition> vouchers = manager.getVouchersSorted();
        int start = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int index = start + i;
            if (index >= vouchers.size()) {
                break;
            }
            VoucherDefinition definition = vouchers.get(index);
            inventory.setItem(CONTENT_SLOTS[i], new ItemBuilder(definition.materiau())
                    .name(definition.nom())
                    .lore(List.of(
                            messages.raw("voucher.editeur-id-lore").replace("{id}", definition.id()),
                            messages.raw("voucher.editeur-cliquer-modifier")))
                    .build());
            slotToVoucherId.put(CONTENT_SLOTS[i], definition.id());
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("general.gui-page-precedente")).build());
        }
        if (start + CONTENT_SLOTS.length < vouchers.size()) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("general.gui-page-suivante")).build());
        }
        inventory.setItem(CREATE_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(messages.raw("voucher.editeur-creer"))
                .lore(List.of(messages.raw("voucher.editeur-creer-lore")))
                .build());
        inventory.setItem(RELOAD_SLOT, new ItemBuilder(Material.PAPER)
                .name(messages.raw("voucher.editeur-recharger"))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == CREATE_SLOT) {
            editorService.requestNewVoucher(player);
            return;
        }
        if (slot == RELOAD_SLOT) {
            manager.load();
            messages.send(player, "voucher.editeur-recharge");
            render();
            return;
        }
        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == NEXT_SLOT) {
            page++;
            render();
            return;
        }
        String voucherId = slotToVoucherId.get(slot);
        if (voucherId != null) {
            VoucherDefinition definition = manager.getVoucher(voucherId);
            if (definition != null) {
                new VoucherEditorGui(player, manager, editorService, definition, messages).open();
            }
        }
    }
}
