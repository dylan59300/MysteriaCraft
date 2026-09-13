package com.mysteriacraft.kits;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu listant les kits disponibles, avec pagination (7 kits par page).
 * Clic gauche = reclamer le kit. Clic droit = previsualiser son contenu reel.
 * Les kits verrouilles (permission manquante) sont affiches grises.
 */
public class KitGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] KIT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PREVIOUS_SLOT = 18;
    private static final int NEXT_SLOT = 26;

    private final KitManager kitManager;
    private final KitService kitService;
    private final MessageManager messages;
    private final Map<Integer, String> slotToKitId = new HashMap<>();

    private Inventory inventory;
    private List<Kit> kits;
    private int page = 0;

    public KitGui(Player viewer, KitManager kitManager, KitService kitService, MessageManager messages) {
        super(viewer);
        this.kitManager = kitManager;
        this.kitService = kitService;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("kits.titre-gui")));
        holder.setInventory(inventory);
        this.kits = new ArrayList<>(kitManager.getKitsSorted());
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
            int kitIndex = firstIndex + i;
            if (kitIndex >= kits.size()) {
                break;
            }
            Kit kit = kits.get(kitIndex);
            boolean unlocked = !kit.hasPermissionRequirement() || viewer.hasPermission(kit.permission());
            inventory.setItem(KIT_SLOTS[i], buildKitItem(kit, unlocked));
            slotToKitId.put(KIT_SLOTS[i], kit.id());
        }

        int maxPage = maxPage();
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW)
                    .name(messages.raw("kits.gui-page-precedente")).build());
        }
        if (page < maxPage - 1) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW)
                    .name(messages.raw("kits.gui-page-suivante")).build());
        }

        Map<String, String> pagePlaceholders = new HashMap<>();
        pagePlaceholders.put("page", String.valueOf(page + 1));
        pagePlaceholders.put("max", String.valueOf(maxPage));
        String pageLabel = messages.raw("kits.gui-page");
        for (Map.Entry<String, String> entry : pagePlaceholders.entrySet()) {
            pageLabel = pageLabel.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        inventory.setItem(22, new ItemBuilder(Material.PAPER).name(pageLabel).build());
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
        if (kitId == null) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClick() == ClickType.RIGHT) {
            Kit kit = kitManager.getKit(kitId);
            if (kit != null) {
                new KitPreviewGui(player, kit, this, messages).open();
            }
            return;
        }

        player.closeInventory();
        kitService.claim(player, kitId);
    }
}
