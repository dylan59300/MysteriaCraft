package com.mysteriacraft.admin.gui;

import com.mysteriacraft.admin.AdminChatService;
import com.mysteriacraft.admin.AdminMaintenanceManager;
import com.mysteriacraft.admin.AdminModule;
import com.mysteriacraft.admin.AdminMuteManager;
import com.mysteriacraft.admin.AdminSummaryRegistry;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Actions disponibles pour un module precis du panel admin (voir AdminPanelGui) : recharger sa
 * config (avec confirmation pour les modules sensibles), ouvrir son editeur en jeu s'il en a un,
 * et basculer son mode maintenance. Delegue entierement a la commande existante du module (aucune
 * logique dupliquee), executee a la place de l'admin via Bukkit#dispatchCommand.
 */
public class AdminModuleGui extends Menu {

    private static final int SIZE = 27;
    private static final int RECHARGER_SLOT = 10;
    private static final int EDITEUR_SLOT = 12;
    private static final int MAINTENANCE_SLOT = 14;
    private static final int RETOUR_SLOT = 22;

    private static final long DELAI_CONFIRMATION_MS = 5000L;

    private final AdminModule module;
    private final AdminMaintenanceManager maintenanceManager;
    private final AdminChatService chatService;
    private final AdminMuteManager muteManager;
    private final MessageManager messages;

    private Inventory inventory;
    private long confirmationRecharger = 0;

    public AdminModuleGui(Player viewer, AdminModule module, AdminMaintenanceManager maintenanceManager,
                           AdminChatService chatService, AdminMuteManager muteManager, MessageManager messages) {
        super(viewer);
        this.module = module;
        this.maintenanceManager = maintenanceManager;
        this.chatService = chatService;
        this.muteManager = muteManager;
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

        List<String> rechargerLore = new ArrayList<>();
        String resume = AdminSummaryRegistry.get(module.label());
        if (resume != null) {
            rechargerLore.add("&7" + resume);
        }
        rechargerLore.add(messages.raw(module.sensible() ? "admin.recharger-lore-sensible" : "admin.recharger-lore"));
        boolean enAttenteConfirmation = System.currentTimeMillis() < confirmationRecharger;
        inventory.setItem(RECHARGER_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(enAttenteConfirmation ? messages.raw("admin.recharger-confirmer") : messages.raw("admin.recharger-nom"))
                .lore(rechargerLore)
                .build());

        if (module.hasEditor()) {
            inventory.setItem(EDITEUR_SLOT, new ItemBuilder(Material.WRITABLE_BOOK)
                    .name(messages.raw("admin.editeur-nom"))
                    .lore(List.of(messages.raw("admin.editeur-lore")))
                    .build());
        }

        boolean enMaintenance = maintenanceManager.isDisabled(module.commandeAGerer());
        inventory.setItem(MAINTENANCE_SLOT, new ItemBuilder(enMaintenance ? Material.RED_WOOL : Material.LIME_WOOL)
                .name(enMaintenance ? messages.raw("admin.maintenance-active-nom") : messages.raw("admin.maintenance-inactive-nom"))
                .lore(List.of(messages.raw("admin.maintenance-lore")))
                .build());

        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("admin.retour")).build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == RETOUR_SLOT) {
            new AdminPanelGui(player, maintenanceManager, chatService, muteManager, messages).open();
            return;
        }
        if (slot == RECHARGER_SLOT) {
            if (!module.sensible() || System.currentTimeMillis() < confirmationRecharger) {
                confirmationRecharger = 0;
                Bukkit.dispatchCommand(player, module.reloadCommand());
            } else {
                confirmationRecharger = System.currentTimeMillis() + DELAI_CONFIRMATION_MS;
                messages.send(player, "admin.confirmation-demandee");
            }
            render();
            return;
        }
        if (slot == EDITEUR_SLOT && module.hasEditor()) {
            Bukkit.dispatchCommand(player, module.editorCommand());
            return;
        }
        if (slot == MAINTENANCE_SLOT) {
            boolean desormaisDesactive = maintenanceManager.toggle(module.commandeAGerer());
            messages.send(player, desormaisDesactive ? "admin.maintenance-activee" : "admin.maintenance-desactivee",
                    java.util.Map.of("module", module.label()));
            render();
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
