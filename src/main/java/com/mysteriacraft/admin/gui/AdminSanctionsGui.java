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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sanctions rapides (voir /admin) : liste les joueurs actuellement en ligne, cliquer sur l'un
 * d'eux ouvre ses actions de sanction (voir AdminSanctionPlayerGui).
 */
public class AdminSanctionsGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int RETOUR_SLOT = 49;

    private final AdminMaintenanceManager maintenanceManager;
    private final AdminChatService chatService;
    private final AdminMuteManager muteManager;
    private final MessageManager messages;

    private Inventory inventory;
    private final Map<Integer, Player> slotToPlayer = new HashMap<>();

    public AdminSanctionsGui(Player viewer, AdminMaintenanceManager maintenanceManager, AdminChatService chatService,
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
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("admin.sanctions-titre")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        inventory.clear();
        slotToPlayer.clear();

        List<Player> joueurs = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < CONTENT_SLOTS.length && i < joueurs.size(); i++) {
            Player cible = joueurs.get(i);
            boolean mute = muteManager.isMuted(cible.getUniqueId());
            List<String> lore = List.of(
                    mute ? messages.raw("admin.sanctions-mute-actif") : messages.raw("admin.sanctions-mute-inactif"),
                    messages.raw("admin.sanctions-cliquer"));
            inventory.setItem(CONTENT_SLOTS[i], buildHead(cible, lore));
            slotToPlayer.put(CONTENT_SLOTS[i], cible);
        }

        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("admin.retour")).build());
    }

    private ItemStack buildHead(Player cible, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(cible);
            meta.setDisplayName(MessageManager.color("&e" + cible.getName()));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(MessageManager.color(line));
            }
            meta.setLore(coloredLore);
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player admin = (Player) event.getWhoClicked();

        if (slot == RETOUR_SLOT) {
            new AdminPanelGui(admin, maintenanceManager, chatService, muteManager, messages).open();
            return;
        }
        Player cible = slotToPlayer.get(slot);
        if (cible != null) {
            new AdminSanctionPlayerGui(admin, cible, maintenanceManager, chatService, muteManager, messages).open();
        }
    }
}
