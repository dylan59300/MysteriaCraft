package com.mysteriacraft.luckyblock.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.luckyblock.LuckyBlockEditorService;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Editeur de familles de Lucky Block en jeu (voir /luckyblockadmin editeur), comme les editeurs du
 * BattlePass/de la Boutique : liste TOUTES les familles (meme hors-saison) et permet d'en ajouter
 * ou d'en gerer une.
 */
public class LuckyBlockFamiliesEditorGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int ADD_SLOT = 49;

    private final Plugin plugin;
    private final LuckyBlockManager manager;
    private final LuckyBlockEditorService editorService;
    private final MessageManager messages;

    private Inventory inventory;
    private final Map<Integer, String> slotToFamilyId = new HashMap<>();

    public LuckyBlockFamiliesEditorGui(Plugin plugin, Player viewer, LuckyBlockManager manager,
                                        LuckyBlockEditorService editorService, MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.editorService = editorService;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("luckyblock.editeur-titre-familles")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        inventory.clear();
        slotToFamilyId.clear();

        List<LuckyBlockFamily> familles = manager.getAllFamilies();
        for (int i = 0; i < CONTENT_SLOTS.length && i < familles.size(); i++) {
            LuckyBlockFamily famille = familles.get(i);
            List<String> lore = List.of(
                    replace(messages.raw("luckyblock.editeur-famille-lore-prix"), "prix", String.valueOf(famille.buyPrice())),
                    replace(messages.raw("luckyblock.editeur-famille-lore-chance"), "chance", String.valueOf(famille.baseGoodChance())),
                    messages.raw("luckyblock.editeur-famille-lore-cliquer"));
            inventory.setItem(CONTENT_SLOTS[i], new ItemBuilder(famille.blockMaterial())
                    .name("&e&l" + famille.displayName()).lore(lore).build());
            slotToFamilyId.put(CONTENT_SLOTS[i], famille.id());
        }

        inventory.setItem(ADD_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(messages.raw("luckyblock.editeur-ajouter-famille"))
                .lore(List.of(messages.raw("luckyblock.editeur-ajouter-famille-lore")))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == ADD_SLOT) {
            editorService.requestNomFamille(player);
            return;
        }
        String familyId = slotToFamilyId.get(slot);
        if (familyId != null) {
            LuckyBlockFamily famille = manager.getFamily(familyId);
            if (famille != null) {
                new LuckyBlockFamilyEditorGui(plugin, player, manager, editorService, famille, messages).open();
            }
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
