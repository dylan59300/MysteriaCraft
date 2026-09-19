package com.mysteriacraft.admin.gui;

import com.mysteriacraft.admin.AdminChatService;
import com.mysteriacraft.admin.AdminMaintenanceManager;
import com.mysteriacraft.admin.AdminModule;
import com.mysteriacraft.admin.AdminMuteManager;
import com.mysteriacraft.admin.AdminRegistry;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel admin unifie (/admin) : liste tous les modules geres (voir AdminRegistry), filtres selon
 * les permissions du joueur, et donne acces a leur reload (et editeur en jeu si le module en a un)
 * sans avoir a connaitre chaque commande separement. Propose aussi 3 actions globales : recharger
 * tous les modules, diffuser un message, et les sanctions rapides.
 */
public class AdminPanelGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int BROADCAST_SLOT = 48;
    private static final int RECHARGER_TOUT_SLOT = 49;
    private static final int SANCTIONS_SLOT = 50;

    private static final long DELAI_CONFIRMATION_MS = 5000L;

    private final AdminMaintenanceManager maintenanceManager;
    private final AdminChatService chatService;
    private final AdminMuteManager muteManager;
    private final MessageManager messages;

    private Inventory inventory;
    private int page = 0;
    private List<AdminModule> modulesVisibles;
    private final Map<Integer, AdminModule> slotToModule = new HashMap<>();
    private long confirmationRechargerTout = 0;

    public AdminPanelGui(Player viewer, AdminMaintenanceManager maintenanceManager, AdminChatService chatService,
                          AdminMuteManager muteManager, MessageManager messages) {
        super(viewer);
        this.maintenanceManager = maintenanceManager;
        this.chatService = chatService;
        this.muteManager = muteManager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("admin.titre-panel")));
        holder.setInventory(inventory);

        this.modulesVisibles = new ArrayList<>();
        for (AdminModule module : AdminRegistry.MODULES) {
            if (viewer.hasPermission(module.permission())) {
                modulesVisibles.add(module);
            }
        }
        render();
        return inventory;
    }

    private void render() {
        inventory.clear();
        slotToModule.clear();

        int start = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int index = start + i;
            if (index >= modulesVisibles.size()) {
                break;
            }
            AdminModule module = modulesVisibles.get(index);
            List<String> lore = new ArrayList<>();
            String resume = AdminSummaryRegistry.get(module.label());
            if (resume != null) {
                lore.add("&7" + resume);
            }
            lore.add(messages.raw("admin.module-lore-recharger"));
            if (module.hasEditor()) {
                lore.add(messages.raw("admin.module-lore-editeur"));
            }
            inventory.setItem(CONTENT_SLOTS[i], new ItemBuilder(module.icon()).name("&e&l" + module.label()).lore(lore).build());
            slotToModule.put(CONTENT_SLOTS[i], module);
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("general.gui-page-precedente")).build());
        }
        if (start + CONTENT_SLOTS.length < modulesVisibles.size()) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("general.gui-page-suivante")).build());
        }

        boolean enAttenteConfirmation = System.currentTimeMillis() < confirmationRechargerTout;
        inventory.setItem(RECHARGER_TOUT_SLOT, new ItemBuilder(Material.TNT)
                .name(enAttenteConfirmation ? messages.raw("admin.recharger-tout-confirmer") : messages.raw("admin.recharger-tout-nom"))
                .lore(List.of(messages.raw("admin.recharger-tout-lore")))
                .build());
        inventory.setItem(BROADCAST_SLOT, new ItemBuilder(Material.BELL)
                .name(messages.raw("admin.broadcast-nom"))
                .lore(List.of(messages.raw("admin.broadcast-lore")))
                .build());
        inventory.setItem(SANCTIONS_SLOT, new ItemBuilder(Material.IRON_SWORD)
                .name(messages.raw("admin.sanctions-nom"))
                .lore(List.of(messages.raw("admin.sanctions-lore")))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

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
        if (slot == BROADCAST_SLOT) {
            chatService.requestBroadcast(player);
            return;
        }
        if (slot == SANCTIONS_SLOT) {
            new AdminSanctionsGui(player, maintenanceManager, chatService, muteManager, messages).open();
            return;
        }
        if (slot == RECHARGER_TOUT_SLOT) {
            if (System.currentTimeMillis() < confirmationRechargerTout) {
                confirmationRechargerTout = 0;
                for (AdminModule module : modulesVisibles) {
                    Bukkit.dispatchCommand(player, module.reloadCommand());
                }
                messages.send(player, "admin.recharger-tout-effectue");
            } else {
                confirmationRechargerTout = System.currentTimeMillis() + DELAI_CONFIRMATION_MS;
                messages.send(player, "admin.confirmation-demandee");
            }
            render();
            return;
        }
        AdminModule module = slotToModule.get(slot);
        if (module != null) {
            new AdminModuleGui(player, module, maintenanceManager, chatService, muteManager, messages).open();
        }
    }
}
