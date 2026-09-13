package com.mysteriacraft.crates.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.crates.Crate;
import com.mysteriacraft.crates.CrateManager;
import com.mysteriacraft.crates.CrateService;
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
 * Menu listant les caisses disponibles avec le nombre de cles virtuelles possedees.
 * Clic gauche = ouvrir la caisse (si au moins une cle). Clic droit = voir la table de loot en %.
 */
public class CrateListGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] CRATE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PREVIOUS_SLOT = 18;
    private static final int NEXT_SLOT = 26;

    private final CrateManager crateManager;
    private final CrateService crateService;
    private final MessageManager messages;
    private final Map<String, Integer> keyCounts;
    private final Map<Integer, String> slotToCrateId = new HashMap<>();

    private Inventory inventory;
    private List<Crate> crates;
    private int page = 0;

    public CrateListGui(Player viewer, CrateManager crateManager, CrateService crateService,
                         MessageManager messages, Map<String, Integer> keyCounts) {
        super(viewer);
        this.crateManager = crateManager;
        this.crateService = crateService;
        this.messages = messages;
        this.keyCounts = keyCounts;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("crates.titre-gui")));
        holder.setInventory(inventory);
        this.crates = new ArrayList<>(crateManager.getCratesSorted());
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(crates.size() / (double) CRATE_SLOTS.length));
    }

    private void render() {
        slotToCrateId.clear();

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int firstIndex = page * CRATE_SLOTS.length;
        for (int i = 0; i < CRATE_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= crates.size()) {
                break;
            }
            Crate crate = crates.get(index);
            inventory.setItem(CRATE_SLOTS[i], buildCrateItem(crate));
            slotToCrateId.put(CRATE_SLOTS[i], crate.id());
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
    }

    private ItemStack buildCrateItem(Crate crate) {
        boolean unlocked = !crate.hasPermissionRequirement() || viewer.hasPermission(crate.permission());
        int keys = keyCounts.getOrDefault(crate.id(), 0);

        List<String> lore = new ArrayList<>(crate.lore());
        lore.add("");
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("cles", String.valueOf(keys));
        String keyLine = messages.raw("crates.gui-cles");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            keyLine = keyLine.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        lore.add(keyLine);
        lore.add("");
        lore.add(messages.raw("crates.gui-clic-gauche"));
        lore.add(messages.raw("crates.gui-clic-droit"));
        if (!unlocked) {
            lore.add("");
            lore.add(messages.raw("kits.gui-verrouille"));
        }

        String name = unlocked ? crate.displayName() : "&7" + crate.displayName();
        return new ItemBuilder(unlocked ? crate.icon() : Material.GRAY_DYE)
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

        String crateId = slotToCrateId.get(slot);
        if (crateId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClick() == ClickType.RIGHT) {
            Crate crate = crateManager.getCrate(crateId);
            if (crate != null) {
                new CrateOddsGui(player, crate, this, messages).open();
            }
            return;
        }

        player.closeInventory();
        crateService.open(player, crateId);
    }
}
