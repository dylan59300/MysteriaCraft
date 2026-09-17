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

import java.util.List;

/**
 * Edite une famille precise de Lucky Block (voir LuckyBlockFamiliesEditorGui) : prix d'achat,
 * chance de bon effet, fenetre saisonniere, acces aux effets propres, ou suppression.
 */
public class LuckyBlockFamilyEditorGui extends Menu {

    private static final int SIZE = 27;
    private static final int PRIX_ACHAT_SLOT = 10;
    private static final int CHANCE_BONNE_SLOT = 11;
    private static final int ACTIF_DU_SLOT = 12;
    private static final int ACTIF_AU_SLOT = 13;
    private static final int EFFETS_SLOT = 15;
    private static final int SUPPRIMER_SLOT = 16;
    private static final int RETOUR_SLOT = 22;

    private final Plugin plugin;
    private final LuckyBlockManager manager;
    private final LuckyBlockEditorService editorService;
    private final String familyId;
    private final MessageManager messages;

    private Inventory inventory;

    public LuckyBlockFamilyEditorGui(Plugin plugin, Player viewer, LuckyBlockManager manager,
                                      LuckyBlockEditorService editorService, LuckyBlockFamily family,
                                      MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.editorService = editorService;
        this.familyId = family.id();
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("luckyblock.editeur-titre-famille")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        LuckyBlockFamily famille = manager.getFamily(familyId);
        if (famille == null) {
            viewer.closeInventory();
            return;
        }
        inventory.clear();

        inventory.setItem(PRIX_ACHAT_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name(replace(messages.raw("luckyblock.editeur-prix-achat-nom"), "prix", String.valueOf(famille.buyPrice())))
                .lore(List.of(messages.raw("luckyblock.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(CHANCE_BONNE_SLOT, new ItemBuilder(Material.EMERALD)
                .name(replace(messages.raw("luckyblock.editeur-chance-bonne-nom"), "chance", String.valueOf(famille.baseGoodChance())))
                .lore(List.of(messages.raw("luckyblock.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(ACTIF_DU_SLOT, new ItemBuilder(Material.SUNFLOWER)
                .name(replace(messages.raw("luckyblock.editeur-actif-du-nom"), "date",
                        famille.actifDu() != null ? famille.actifDu() : messages.raw("luckyblock.editeur-aucune-date")))
                .lore(List.of(messages.raw("luckyblock.editeur-date-lore-format"), messages.raw("luckyblock.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(ACTIF_AU_SLOT, new ItemBuilder(Material.CARVED_PUMPKIN)
                .name(replace(messages.raw("luckyblock.editeur-actif-au-nom"), "date",
                        famille.actifAu() != null ? famille.actifAu() : messages.raw("luckyblock.editeur-aucune-date")))
                .lore(List.of(messages.raw("luckyblock.editeur-date-lore-format"), messages.raw("luckyblock.editeur-cliquer-modifier")))
                .build());
        inventory.setItem(EFFETS_SLOT, new ItemBuilder(Material.CHEST)
                .name(replace(messages.raw("luckyblock.editeur-gerer-effets-nom"), "nombre", String.valueOf(famille.effects().size())))
                .lore(List.of(messages.raw("luckyblock.editeur-gerer-effets-lore")))
                .build());
        inventory.setItem(SUPPRIMER_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("luckyblock.editeur-supprimer-famille"))
                .lore(List.of(messages.raw("luckyblock.editeur-supprimer-famille-lore")))
                .build());
        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("luckyblock.editeur-retour")).build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == RETOUR_SLOT) {
            new LuckyBlockFamiliesEditorGui(plugin, player, manager, editorService, messages).open();
            return;
        }
        if (slot == PRIX_ACHAT_SLOT) {
            editorService.requestFamilleField(player, familyId, "prix-achat");
            return;
        }
        if (slot == CHANCE_BONNE_SLOT) {
            editorService.requestFamilleField(player, familyId, "chance-bonne");
            return;
        }
        if (slot == ACTIF_DU_SLOT) {
            editorService.requestFamilleField(player, familyId, "actif-du");
            return;
        }
        if (slot == ACTIF_AU_SLOT) {
            editorService.requestFamilleField(player, familyId, "actif-au");
            return;
        }
        if (slot == EFFETS_SLOT) {
            LuckyBlockFamily famille = manager.getFamily(familyId);
            if (famille != null) {
                new LuckyBlockEffectsEditorGui(plugin, player, manager, editorService, famille, messages).open();
            }
            return;
        }
        if (slot == SUPPRIMER_SLOT) {
            manager.removeFamily(familyId);
            new LuckyBlockFamiliesEditorGui(plugin, player, manager, editorService, messages).open();
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
