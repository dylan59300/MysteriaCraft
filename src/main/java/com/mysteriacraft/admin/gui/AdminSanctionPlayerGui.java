package com.mysteriacraft.admin.gui;

import com.mysteriacraft.admin.AdminChatService;
import com.mysteriacraft.admin.AdminMaintenanceManager;
import com.mysteriacraft.admin.AdminMuteManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Actions de sanction rapide pour un joueur precis (voir AdminSanctionsGui) : kick, ban, mute
 * (10 min / 1h / permanent), demute. Raisons fixes (voir messages.yml), pas de saisie au chat pour
 * rester rapide a utiliser.
 */
public class AdminSanctionPlayerGui extends Menu {

    private static final int SIZE = 27;
    private static final int KICK_SLOT = 10;
    private static final int BAN_SLOT = 11;
    private static final int MUTE_10MIN_SLOT = 13;
    private static final int MUTE_1H_SLOT = 14;
    private static final int MUTE_PERMANENT_SLOT = 15;
    private static final int DEMUTE_SLOT = 16;
    private static final int RETOUR_SLOT = 22;

    private final UUID cibleUuid;
    private final String cibleNom;
    private final AdminMaintenanceManager maintenanceManager;
    private final AdminChatService chatService;
    private final AdminMuteManager muteManager;
    private final MessageManager messages;

    private Inventory inventory;

    public AdminSanctionPlayerGui(Player viewer, Player cible, AdminMaintenanceManager maintenanceManager,
                                   AdminChatService chatService, AdminMuteManager muteManager, MessageManager messages) {
        super(viewer);
        this.cibleUuid = cible.getUniqueId();
        this.cibleNom = cible.getName();
        this.maintenanceManager = maintenanceManager;
        this.chatService = chatService;
        this.muteManager = muteManager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        String titre = messages.raw("admin.sanctions-titre-joueur").replace("{joueur}", cibleNom);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(titre));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        inventory.clear();
        inventory.setItem(KICK_SLOT, new ItemBuilder(Material.LEATHER_BOOTS)
                .name(messages.raw("admin.sanction-kick-nom")).lore(List.of(messages.raw("admin.sanction-cliquer"))).build());
        inventory.setItem(BAN_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("admin.sanction-ban-nom")).lore(List.of(messages.raw("admin.sanction-cliquer"))).build());
        inventory.setItem(MUTE_10MIN_SLOT, new ItemBuilder(Material.PAPER)
                .name(messages.raw("admin.sanction-mute-10min-nom")).lore(List.of(messages.raw("admin.sanction-cliquer"))).build());
        inventory.setItem(MUTE_1H_SLOT, new ItemBuilder(Material.PAPER)
                .name(messages.raw("admin.sanction-mute-1h-nom")).lore(List.of(messages.raw("admin.sanction-cliquer"))).build());
        inventory.setItem(MUTE_PERMANENT_SLOT, new ItemBuilder(Material.PAPER)
                .name(messages.raw("admin.sanction-mute-permanent-nom")).lore(List.of(messages.raw("admin.sanction-cliquer"))).build());
        inventory.setItem(DEMUTE_SLOT, new ItemBuilder(Material.EMERALD)
                .name(messages.raw("admin.sanction-demute-nom")).lore(List.of(messages.raw("admin.sanction-cliquer"))).build());
        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("admin.retour")).build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player admin = (Player) event.getWhoClicked();
        Player cible = Bukkit.getPlayer(cibleUuid);

        if (slot == RETOUR_SLOT) {
            new AdminSanctionsGui(admin, maintenanceManager, chatService, muteManager, messages).open();
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("joueur", cibleNom);

        if (slot == KICK_SLOT) {
            if (cible != null) {
                cible.kickPlayer(MessageManager.color(messages.raw("admin.sanction-raison-kick")));
            }
            messages.send(admin, "admin.sanction-kick-effectue", placeholders);
            return;
        }
        if (slot == BAN_SLOT) {
            String raison = messages.raw("admin.sanction-raison-ban");
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(cibleNom, raison, null, null);
            if (cible != null) {
                cible.kickPlayer(MessageManager.color(raison));
            }
            messages.send(admin, "admin.sanction-ban-effectue", placeholders);
            return;
        }
        if (slot == MUTE_10MIN_SLOT) {
            muteManager.mute(cibleUuid, System.currentTimeMillis() + 10 * 60_000L, messages.raw("admin.sanction-raison-mute"));
            messages.send(admin, "admin.sanction-mute-effectue", placeholders);
            render();
            return;
        }
        if (slot == MUTE_1H_SLOT) {
            muteManager.mute(cibleUuid, System.currentTimeMillis() + 60 * 60_000L, messages.raw("admin.sanction-raison-mute"));
            messages.send(admin, "admin.sanction-mute-effectue", placeholders);
            render();
            return;
        }
        if (slot == MUTE_PERMANENT_SLOT) {
            muteManager.mute(cibleUuid, AdminMuteManager.PERMANENT, messages.raw("admin.sanction-raison-mute"));
            messages.send(admin, "admin.sanction-mute-effectue", placeholders);
            render();
            return;
        }
        if (slot == DEMUTE_SLOT) {
            muteManager.unmute(cibleUuid);
            messages.send(admin, "admin.sanction-demute-effectue", placeholders);
            render();
        }
    }
}
