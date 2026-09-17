package com.mysteriacraft.luckyblock.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.luckyblock.BadEffectType;
import com.mysteriacraft.luckyblock.EffectKind;
import com.mysteriacraft.luckyblock.LuckyBlockEditorService;
import com.mysteriacraft.luckyblock.LuckyBlockEffect;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Effets PROPRES a une famille de Lucky Block (voir LuckyBlockManager#getOwnEffects, n'inclut pas
 * le pool commun a toutes les familles). Clic = modifier la chance au chat, shift-clic = supprimer.
 * Le type d'un effet n'est pas modifiable une fois cree (supprimez et recreez pour en changer).
 */
public class LuckyBlockEffectsEditorGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int AJOUTER_BON_SLOT = 45;
    private static final int AJOUTER_TNT_SLOT = 46;
    private static final int AJOUTER_MOBS_SLOT = 47;
    private static final int AJOUTER_POTION_SLOT = 48;
    private static final int AJOUTER_FOUDRE_SLOT = 49;
    private static final int RETOUR_SLOT = 53;

    private final Plugin plugin;
    private final LuckyBlockManager manager;
    private final LuckyBlockEditorService editorService;
    private final String familyId;
    private final MessageManager messages;

    private Inventory inventory;
    private final Map<Integer, Integer> slotToIndex = new HashMap<>();

    public LuckyBlockEffectsEditorGui(Plugin plugin, Player viewer, LuckyBlockManager manager,
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
        String titre = replace(messages.raw("luckyblock.editeur-titre-effets"), "famille", familyId);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(titre));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        inventory.clear();
        slotToIndex.clear();

        List<LuckyBlockEffect> effets = manager.getOwnEffects(familyId);
        for (int i = 0; i < CONTENT_SLOTS.length && i < effets.size(); i++) {
            LuckyBlockEffect effet = effets.get(i);
            List<String> lore = List.of(
                    replace(messages.raw("luckyblock.editeur-effet-lore-chance"), "chance", String.valueOf(effet.chance())),
                    messages.raw("luckyblock.editeur-effet-lore-clic"),
                    messages.raw("luckyblock.editeur-effet-lore-shift-clic"));
            Material icone = effet.kind() == EffectKind.BON ? Material.EMERALD : iconeMauvais(effet);
            inventory.setItem(CONTENT_SLOTS[i], new ItemBuilder(icone).name(effet.displayName()).lore(lore).build());
            slotToIndex.put(CONTENT_SLOTS[i], i);
        }

        inventory.setItem(AJOUTER_BON_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(messages.raw("luckyblock.editeur-ajouter-bon"))
                .lore(List.of(messages.raw("luckyblock.editeur-ajouter-bon-lore")))
                .build());
        inventory.setItem(AJOUTER_TNT_SLOT, new ItemBuilder(Material.TNT)
                .name(messages.raw("luckyblock.editeur-ajouter-tnt")).build());
        inventory.setItem(AJOUTER_MOBS_SLOT, new ItemBuilder(Material.ZOMBIE_HEAD)
                .name(messages.raw("luckyblock.editeur-ajouter-mobs")).build());
        inventory.setItem(AJOUTER_POTION_SLOT, new ItemBuilder(Material.POTION)
                .name(messages.raw("luckyblock.editeur-ajouter-potion")).build());
        inventory.setItem(AJOUTER_FOUDRE_SLOT, new ItemBuilder(Material.TRIDENT)
                .name(messages.raw("luckyblock.editeur-ajouter-foudre")).build());
        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("luckyblock.editeur-retour")).build());
    }

    private Material iconeMauvais(LuckyBlockEffect effet) {
        if (effet.badType() == null) {
            return Material.BARRIER;
        }
        return switch (effet.badType()) {
            case TNT -> Material.TNT;
            case MOBS -> Material.ZOMBIE_HEAD;
            case POTION -> Material.POTION;
            case FOUDRE -> Material.TRIDENT;
        };
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == RETOUR_SLOT) {
            LuckyBlockFamily famille = manager.getFamily(familyId);
            if (famille != null) {
                new LuckyBlockFamilyEditorGui(plugin, player, manager, editorService, famille, messages).open();
            }
            return;
        }
        if (slot == AJOUTER_BON_SLOT) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                messages.send(player, "luckyblock.editeur-main-vide");
                return;
            }
            manager.addGoodEffectFromItem(familyId, held, 10.0);
            render();
            return;
        }
        if (slot == AJOUTER_TNT_SLOT) {
            manager.addBadEffect(familyId, BadEffectType.TNT, 10.0);
            render();
            return;
        }
        if (slot == AJOUTER_MOBS_SLOT) {
            manager.addBadEffect(familyId, BadEffectType.MOBS, 10.0);
            render();
            return;
        }
        if (slot == AJOUTER_POTION_SLOT) {
            manager.addBadEffect(familyId, BadEffectType.POTION, 10.0);
            render();
            return;
        }
        if (slot == AJOUTER_FOUDRE_SLOT) {
            manager.addBadEffect(familyId, BadEffectType.FOUDRE, 10.0);
            render();
            return;
        }

        Integer index = slotToIndex.get(slot);
        if (index != null) {
            if (event.isShiftClick()) {
                manager.removeEffectAt(familyId, index);
                render();
            } else {
                editorService.requestEffetChance(player, familyId, index);
            }
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
