package com.mysteriacraft.kits;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu listant tous les kits disponibles. Un clic reclame le kit correspondant.
 * Les kits verrouilles (permission manquante) sont affiches grises avec un cadenas.
 */
public class KitGui extends Menu {

    private static final int SIZE = 27;

    private final KitManager kitManager;
    private final KitService kitService;
    private final MessageManager messages;
    private final Map<Integer, String> slotToKitId = new HashMap<>();

    public KitGui(Player viewer, KitManager kitManager, KitService kitService, MessageManager messages) {
        super(viewer);
        this.kitManager = kitManager;
        this.kitService = kitService;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        slotToKitId.clear();
        MenuHolder holder = new MenuHolder(this);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("kits.titre-gui")));
        holder.setInventory(inventory);

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        List<Kit> kits = new ArrayList<>(kitManager.getKitsSorted());
        int slot = 10;
        for (Kit kit : kits) {
            if (slot > 16) {
                break; // Menu limite a 7 kits (ligne du milieu) ; au-dela, prevoir une pagination.
            }

            boolean unlocked = !kit.hasPermissionRequirement() || viewer.hasPermission(kit.permission());
            inventory.setItem(slot, buildKitItem(kit, unlocked));
            slotToKitId.put(slot, kit.id());
            slot++;
        }

        return inventory;
    }

    private ItemStack buildKitItem(Kit kit, boolean unlocked) {
        List<String> lore = new ArrayList<>(kit.lore());
        if (!unlocked) {
            lore.add("");
            lore.add(messages.raw("kits.gui-verrouille"));
        }

        String name = unlocked ? kit.displayName() : "&7" + kit.displayName();
        return new ItemBuilder(unlocked ? kit.icon() : Material.GRAY_DYE)
                .name(name)
                .lore(lore)
                .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        String kitId = slotToKitId.get(event.getSlot());
        if (kitId == null) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        player.closeInventory();
        kitService.claim(player, kitId);
    }
}
