package com.mysteriacraft.battlepass.gui;

import com.mysteriacraft.battlepass.BattlePassEditorService;
import com.mysteriacraft.battlepass.BattlePassLevel;
import com.mysteriacraft.battlepass.BattlePassManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Editeur de paliers en jeu (voir /battlepassadmin editeur), comme dans le vrai plugin BattlePass :
 * liste tous les paliers configures et permet d'en ajouter/modifier/supprimer sans toucher a
 * battlepass.yml a la main.
 */
public class BattlePassEditorGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int PREVIOUS_SLOT = 45;
    private static final int ADD_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final Plugin plugin;
    private final BattlePassManager manager;
    private final BattlePassEditorService editorService;
    private final MessageManager messages;

    private Inventory inventory;
    private int page = 0;
    private final Map<Integer, Integer> slotToLevel = new HashMap<>();

    public BattlePassEditorGui(Plugin plugin, Player viewer, BattlePassManager manager,
                                BattlePassEditorService editorService, MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.editorService = editorService;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("battlepass.editeur-titre-liste")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        inventory.clear();
        slotToLevel.clear();

        List<BattlePassLevel> levels = new ArrayList<>(manager.getLevels());
        levels.sort((a, b) -> Integer.compare(a.level(), b.level()));

        int start = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int levelIndex = start + i;
            if (levelIndex >= levels.size()) {
                break;
            }
            BattlePassLevel level = levels.get(levelIndex);
            List<String> lore = new ArrayList<>();
            lore.add(replace(messages.raw("battlepass.editeur-palier-lore-xp"), "xp", String.valueOf(level.xpRequired())));
            lore.add(replace(messages.raw("battlepass.editeur-palier-lore-chapitre"), "chapitre", String.valueOf(level.chapitre())));
            lore.add(messages.raw("battlepass.editeur-palier-lore-cliquer"));

            String name = replace(messages.raw("battlepass.editeur-palier-nom"), "niveau", String.valueOf(level.level()));
            inventory.setItem(CONTENT_SLOTS[i], new ItemBuilder(Material.NETHER_STAR).name(name).lore(lore).build());
            slotToLevel.put(CONTENT_SLOTS[i], level.level());
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("general.gui-page-precedente")).build());
        }
        if (start + CONTENT_SLOTS.length < levels.size()) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("general.gui-page-suivante")).build());
        }
        inventory.setItem(ADD_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(messages.raw("battlepass.editeur-ajouter-palier"))
                .lore(List.of(messages.raw("battlepass.editeur-ajouter-palier-lore")))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == ADD_SLOT) {
            int newLevel = manager.addLevel();
            new BattlePassLevelEditorGui(plugin, viewer, manager, editorService, messages, newLevel).open();
            return;
        }
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
        Integer level = slotToLevel.get(slot);
        if (level != null) {
            new BattlePassLevelEditorGui(plugin, viewer, manager, editorService, messages, level).open();
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
