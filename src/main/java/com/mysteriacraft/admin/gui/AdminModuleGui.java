package com.mysteriacraft.admin.gui;

import com.mysteriacraft.admin.AdminModule;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Actions disponibles pour un module precis du panel admin (voir AdminPanelGui) : recharger sa
 * config, ouvrir son editeur en jeu s'il en a un. Delegue entierement a la commande existante du
 * module (aucune logique dupliquee), executee a la place de l'admin via Bukkit#dispatchCommand.
 */
public class AdminModuleGui extends Menu {

    private static final int SIZE = 27;
    private static final int RECHARGER_SLOT = 11;
    private static final int EDITEUR_SLOT = 13;
    private static final int RETOUR_SLOT = 22;

    private final AdminModule module;
    private final MessageManager messages;

    private Inventory inventory;

    public AdminModuleGui(Player viewer, AdminModule module, MessageManager messages) {
        super(viewer);
        this.module = module;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        String titre = replace(messages.raw("admin.titre-module"), "module", module.label());
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(titre));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        inventory.clear();
        inventory.setItem(RECHARGER_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(messages.raw("admin.recharger-nom"))
                .lore(List.of(messages.raw("admin.recharger-lore")))
                .build());
        if (module.hasEditor()) {
            inventory.setItem(EDITEUR_SLOT, new ItemBuilder(Material.WRITABLE_BOOK)
                    .name(messages.raw("admin.editeur-nom"))
                    .lore(List.of(messages.raw("admin.editeur-lore")))
                    .build());
        }
        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("admin.retour")).build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == RETOUR_SLOT) {
            new AdminPanelGui(player, messages).open();
            return;
        }
        if (slot == RECHARGER_SLOT) {
            Bukkit.dispatchCommand(player, module.reloadCommand());
            return;
        }
        if (slot == EDITEUR_SLOT && module.hasEditor()) {
            Bukkit.dispatchCommand(player, module.editorCommand());
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
