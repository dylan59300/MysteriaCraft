package com.mysteriacraft.kit.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.kit.KitManager;
import com.mysteriacraft.kit.KitService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu listant les kits disponibles avec leur statut (disponible / cooldown restant / deja
 * recupere pour un kit unique). Le statut est un instantane calcule au moment de l'ouverture
 * (voir KitCommand) : cliquer un kit relance toujours la verification reelle cote serveur.
 */
public class KitGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] KIT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PREVIOUS_SLOT = 18;
    private static final int NEXT_SLOT = 26;

    private final KitManager manager;
    private final KitService service;
    private final MessageManager messages;
    private final Map<String, Long> remainingByKitId;
    private final Map<Integer, String> slotToKitId = new HashMap<>();

    private Inventory inventory;
    private List<KitManager.Kit> kits;
    private int page = 0;

    public KitGui(Player viewer, KitManager manager, KitService service,
                  MessageManager messages, Map<String, Long> remainingByKitId) {
        super(viewer);
        this.manager = manager;
        this.service = service;
        this.messages = messages;
        this.remainingByKitId = remainingByKitId;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("kit.titre-gui")));
        holder.setInventory(inventory);
        this.kits = manager.getKitsSorted();
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(kits.size() / (double) KIT_SLOTS.length));
    }

    private void render() {
        slotToKitId.clear();

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int firstIndex = page * KIT_SLOTS.length;
        for (int i = 0; i < KIT_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= kits.size()) {
                break;
            }
            KitManager.Kit kit = kits.get(index);
            inventory.setItem(KIT_SLOTS[i], buildKitIcon(kit));
            slotToKitId.put(KIT_SLOTS[i], kit.id());
        }

        int maxPage = maxPage();
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW)
                    .name(messages.raw("general.gui-page-precedente")).build());
        }
        if (page < maxPage - 1) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW)
                    .name(messages.raw("general.gui-page-suivante")).build());
        }
    }

    private ItemStack buildKitIcon(KitManager.Kit kit) {
        ItemStack icon = kit.icon() != null ? kit.icon().clone() : new ItemStack(Material.CHEST);
        Long remaining = remainingByKitId.get(kit.id());
        List<String> lore = new ArrayList<>();
        if (remaining == null || remaining == 0L) {
            lore.add(messages.raw("kit.gui-disponible"));
        } else if (remaining < 0) {
            lore.add(messages.raw("kit.gui-deja-recupere"));
        } else {
            lore.add(messages.raw("kit.gui-cooldown"));
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> combined = new ArrayList<>(meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of());
            combined.add("");
            combined.addAll(lore);
            meta.setLore(combined);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == NEXT_SLOT && page < maxPage() - 1) {
            page++;
            render();
            return;
        }

        String kitId = slotToKitId.get(slot);
        if (kitId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        player.closeInventory();
        service.claim(player, kitId);
    }
}
