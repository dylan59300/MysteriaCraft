package com.mysteriacraft.battlepass.gui;

import com.mysteriacraft.battlepass.BattlePassEditorService;
import com.mysteriacraft.battlepass.BattlePassLevel;
import com.mysteriacraft.battlepass.BattlePassManager;
import com.mysteriacraft.battlepass.BattlePassReward;
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
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Edite un palier precis du BattlePass (voir BattlePassEditorGui) : xp requise (saisie au chat),
 * chapitre (clic +1/-1), recompenses gratuite/premium (definies a partir de l'objet tenu en main),
 * et suppression du palier.
 */
public class BattlePassLevelEditorGui extends Menu {

    private static final int SIZE = 27;
    private static final int XP_SLOT = 10;
    private static final int CHAPITRE_SLOT = 12;
    private static final int RECOMPENSE_GRATUITE_SLOT = 14;
    private static final int RECOMPENSE_PREMIUM_SLOT = 16;
    private static final int SUPPRIMER_SLOT = 22;
    private static final int RETOUR_SLOT = 18;

    private final Plugin plugin;
    private final BattlePassManager manager;
    private final BattlePassEditorService editorService;
    private final MessageManager messages;
    private final int level;

    private Inventory inventory;

    public BattlePassLevelEditorGui(Plugin plugin, Player viewer, BattlePassManager manager,
                                     BattlePassEditorService editorService, MessageManager messages, int level) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.editorService = editorService;
        this.messages = messages;
        this.level = level;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        String titre = replace(messages.raw("battlepass.editeur-titre-palier"), "niveau", String.valueOf(level));
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(titre));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        BattlePassLevel data = manager.getLevel(level);
        if (data == null) {
            viewer.closeInventory();
            return;
        }
        inventory.clear();

        inventory.setItem(XP_SLOT, new ItemBuilder(Material.CLOCK)
                .name(replace(messages.raw("battlepass.editeur-xp-nom"), "xp", String.valueOf(data.xpRequired())))
                .lore(List.of(messages.raw("battlepass.editeur-xp-lore")))
                .build());

        inventory.setItem(CHAPITRE_SLOT, new ItemBuilder(Material.BOOK)
                .name(replace(messages.raw("battlepass.editeur-chapitre-nom"), "chapitre", String.valueOf(data.chapitre())))
                .lore(List.of(messages.raw("battlepass.editeur-chapitre-lore")))
                .build());

        inventory.setItem(RECOMPENSE_GRATUITE_SLOT, buildRewardIcon(data.freeReward(), "battlepass.editeur-recompense-gratuite-nom"));
        inventory.setItem(RECOMPENSE_PREMIUM_SLOT, buildRewardIcon(data.premiumReward(), "battlepass.editeur-recompense-premium-nom"));

        inventory.setItem(SUPPRIMER_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("battlepass.editeur-supprimer-nom"))
                .lore(List.of(messages.raw("battlepass.editeur-supprimer-lore")))
                .build());

        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("battlepass.editeur-retour")).build());
    }

    private ItemStack buildRewardIcon(BattlePassReward reward, String nomKey) {
        String etat = reward == null ? messages.raw("battlepass.editeur-recompense-aucune") : reward.displayName();
        String name = replace(messages.raw(nomKey), "recompense", etat);
        return new ItemBuilder(reward == null ? Material.GRAY_STAINED_GLASS_PANE : Material.CHEST)
                .name(name)
                .lore(List.of(
                        messages.raw("battlepass.editeur-recompense-lore-clic"),
                        messages.raw("battlepass.editeur-recompense-lore-effacer"),
                        messages.raw("battlepass.editeur-recompense-lore-commande")))
                .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == RETOUR_SLOT) {
            new BattlePassEditorGui(plugin, viewer, manager, editorService, messages).open();
            return;
        }
        if (slot == XP_SLOT) {
            editorService.requestXpInput(player, level);
            return;
        }
        if (slot == CHAPITRE_SLOT) {
            BattlePassLevel data = manager.getLevel(level);
            if (data == null) {
                return;
            }
            int delta = event.isRightClick() ? -1 : 1;
            manager.setLevelChapitre(level, data.chapitre() + delta);
            render();
            return;
        }
        if (slot == RECOMPENSE_GRATUITE_SLOT || slot == RECOMPENSE_PREMIUM_SLOT) {
            String piste = slot == RECOMPENSE_GRATUITE_SLOT ? "gratuit" : "premium";
            if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                editorService.requestRewardCommand(player, level, piste);
                return;
            }
            ItemStack handItem = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT
                    ? null : player.getInventory().getItemInMainHand();
            manager.setLevelReward(level, piste, handItem);
            render();
            return;
        }
        if (slot == SUPPRIMER_SLOT) {
            manager.removeLevel(level);
            new BattlePassEditorGui(plugin, viewer, manager, editorService, messages).open();
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
