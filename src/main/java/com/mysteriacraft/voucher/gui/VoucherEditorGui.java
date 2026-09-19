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

import java.util.List;

/** Edite un type de Voucher precis (voir VoucherAdminListGui) : nom, materiau, commande, lore,
 * ou suppression. Tous les champs sont saisis au chat (voir VoucherEditorService). */
public class VoucherEditorGui extends Menu {

    private static final int SIZE = 27;
    private static final int NOM_SLOT = 10;
    private static final int MATERIAU_SLOT = 11;
    private static final int COMMANDE_SLOT = 12;
    private static final int LORE_SLOT = 13;
    private static final int SUPPRIMER_SLOT = 15;
    private static final int RETOUR_SLOT = 22;

    private final VoucherManager manager;
    private final VoucherEditorService editorService;
    private final String voucherId;
    private final MessageManager messages;

    private Inventory inventory;

    public VoucherEditorGui(Player viewer, VoucherManager manager, VoucherEditorService editorService,
                             VoucherDefinition definition, MessageManager messages) {
        super(viewer);
        this.manager = manager;
        this.editorService = editorService;
        this.voucherId = definition.id();
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("voucher.editeur-titre-voucher")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        VoucherDefinition definition = manager.getVoucher(voucherId);
        if (definition == null) {
            viewer.closeInventory();
            return;
        }
        inventory.clear();

        inventory.setItem(NOM_SLOT, new ItemBuilder(Material.NAME_TAG)
                .name(replace(messages.raw("voucher.editeur-nom-nom"), "nom", definition.nom()))
                .lore(List.of(messages.raw("voucher.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(MATERIAU_SLOT, new ItemBuilder(definition.materiau())
                .name(replace(messages.raw("voucher.editeur-materiau-nom"), "materiau", definition.materiau().name()))
                .lore(List.of(messages.raw("voucher.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(COMMANDE_SLOT, new ItemBuilder(Material.COMMAND_BLOCK)
                .name(replace(messages.raw("voucher.editeur-commande-nom"), "commande",
                        definition.hasCommande() ? definition.commande() : messages.raw("voucher.editeur-aucune-commande")))
                .lore(List.of(messages.raw("voucher.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(LORE_SLOT, new ItemBuilder(Material.BOOK)
                .name(messages.raw("voucher.editeur-lore-nom"))
                .lore(List.of(messages.raw("voucher.editeur-lore-lore"), messages.raw("voucher.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(SUPPRIMER_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("voucher.editeur-supprimer"))
                .lore(List.of(messages.raw("voucher.editeur-supprimer-lore")))
                .build());
        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("voucher.editeur-retour")).build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == RETOUR_SLOT) {
            new VoucherAdminListGui(player, manager, editorService, messages).open();
            return;
        }
        if (slot == NOM_SLOT) {
            editorService.requestVoucherField(player, voucherId, "nom");
            return;
        }
        if (slot == MATERIAU_SLOT) {
            editorService.requestVoucherField(player, voucherId, "materiau");
            return;
        }
        if (slot == COMMANDE_SLOT) {
            editorService.requestVoucherField(player, voucherId, "commande");
            return;
        }
        if (slot == LORE_SLOT) {
            editorService.requestVoucherField(player, voucherId, "lore");
            return;
        }
        if (slot == SUPPRIMER_SLOT) {
            manager.removeVoucher(voucherId);
            new VoucherAdminListGui(player, manager, editorService, messages).open();
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
