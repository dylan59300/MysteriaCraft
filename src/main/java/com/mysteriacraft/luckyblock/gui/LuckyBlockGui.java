package com.mysteriacraft.luckyblock.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
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
 * Menu listant les familles de Lucky Block, achetables directement (si prix-achat > 0).
 * Clic gauche = acheter 1. Clic droit = voir la table de chances (bon/mauvais) de la famille.
 */
public class LuckyBlockGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] FAMILY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PREVIOUS_SLOT = 18;
    private static final int NEXT_SLOT = 26;

    private final LuckyBlockManager manager;
    private final LuckyBlockService service;
    private final EconomyManager economyManager;
    private final MessageManager messages;
    private final Map<Integer, String> slotToFamilyId = new HashMap<>();

    private Inventory inventory;
    private List<LuckyBlockFamily> families;
    private int page = 0;

    public LuckyBlockGui(Player viewer, LuckyBlockManager manager, LuckyBlockService service,
                          EconomyManager economyManager, MessageManager messages) {
        super(viewer);
        this.manager = manager;
        this.service = service;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("luckyblock.titre-gui")));
        holder.setInventory(inventory);
        this.families = new ArrayList<>(manager.getFamiliesSorted());
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(families.size() / (double) FAMILY_SLOTS.length));
    }

    private void render() {
        slotToFamilyId.clear();

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int firstIndex = page * FAMILY_SLOTS.length;
        for (int i = 0; i < FAMILY_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= families.size()) {
                break;
            }
            LuckyBlockFamily family = families.get(index);
            inventory.setItem(FAMILY_SLOTS[i], buildFamilyItem(family));
            slotToFamilyId.put(FAMILY_SLOTS[i], family.id());
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

    private ItemStack buildFamilyItem(LuckyBlockFamily family) {
        List<String> lore = new ArrayList<>();
        lore.add(replace(messages.raw("luckyblock.gui-chance-base"), "chance", String.valueOf((int) family.baseGoodChance())));
        lore.add("");
        if (family.isPurchasable()) {
            lore.add(replace(messages.raw("luckyblock.gui-prix"), "prix", economyManager.format(family.buyPrice())));
            lore.add(messages.raw("luckyblock.gui-clic-acheter"));
        } else {
            lore.add(messages.raw("luckyblock.gui-non-achetable"));
        }
        lore.add(messages.raw("luckyblock.gui-clic-droit"));

        return new ItemBuilder(family.blockMaterial()).name(family.displayName()).lore(lore).build();
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
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

        String familyId = slotToFamilyId.get(slot);
        if (familyId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        LuckyBlockFamily family = manager.getFamily(familyId);
        if (family == null) {
            return;
        }

        if (event.getClick() == ClickType.RIGHT) {
            new LuckyBlockOddsGui(player, family, this, messages).open();
            return;
        }

        player.closeInventory();
        service.buy(player, familyId, 1);
    }
}
