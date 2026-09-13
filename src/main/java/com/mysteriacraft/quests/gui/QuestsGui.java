package com.mysteriacraft.quests.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.quests.QuestDefinition;
import com.mysteriacraft.quests.QuestManager;
import com.mysteriacraft.quests.QuestPeriod;
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
 * Menu en lecture seule listant les quetes quotidiennes puis hebdomadaires avec la progression
 * du joueur. Les recompenses (xp + directe) sont donnees automatiquement a la completion : ce
 * menu ne fait qu'afficher l'etat, aucune reclamation manuelle n'est necessaire.
 */
public class QuestsGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PREVIOUS_SLOT = 18;
    private static final int NEXT_SLOT = 26;

    private final QuestManager questManager;
    private final MessageManager messages;
    private final Map<String, QuestManager.ProgressSnapshot> progress;

    private Inventory inventory;
    private List<QuestDefinition> quests;
    private int page = 0;

    public QuestsGui(Player viewer, QuestManager questManager, MessageManager messages,
                      Map<String, QuestManager.ProgressSnapshot> progress) {
        super(viewer);
        this.questManager = questManager;
        this.messages = messages;
        this.progress = progress;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("quests.titre-gui")));
        holder.setInventory(inventory);

        this.quests = new ArrayList<>(questManager.getDailyQuests());
        quests.addAll(questManager.getWeeklyQuests());

        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(quests.size() / (double) QUEST_SLOTS.length));
    }

    private void render() {
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int firstIndex = page * QUEST_SLOTS.length;
        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= quests.size()) {
                break;
            }
            inventory.setItem(QUEST_SLOTS[i], buildQuestItem(quests.get(index)));
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

    private ItemStack buildQuestItem(QuestDefinition quest) {
        QuestManager.ProgressSnapshot snapshot = progress.getOrDefault(quest.id(), new QuestManager.ProgressSnapshot(0, false));

        List<String> lore = new ArrayList<>();
        lore.add(quest.period() == QuestPeriod.DAILY ? messages.raw("quests.tag-quotidienne") : messages.raw("quests.tag-hebdomadaire"));
        lore.add("");
        lore.add(replace(messages.raw("quests.gui-progression"), "progression", String.valueOf(snapshot.progression()), "objectif", String.valueOf(quest.objective())));
        lore.add(replace(messages.raw("quests.gui-xp"), "xp", String.valueOf(quest.xpReward())));
        if (quest.reward() != null) {
            lore.add(replace(messages.raw("quests.gui-recompense"), "recompense", quest.reward().displayName()));
        }
        lore.add("");
        lore.add(snapshot.complete() ? messages.raw("quests.gui-terminee") : messages.raw("quests.gui-en-cours"));

        Material material = snapshot.complete() ? Material.LIME_DYE : quest.icon().getType();
        String name = snapshot.complete() ? "&a&l" + stripColor(quest.displayName()) : quest.displayName();

        return new ItemBuilder(material).name(name).lore(lore).build();
    }

    private String stripColor(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "");
    }

    private String replace(String text, String key1, String value1, String key2, String value2) {
        return text.replace("{" + key1 + "}", value1).replace("{" + key2 + "}", value2);
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
        } else if (slot == NEXT_SLOT && page < maxPage() - 1) {
            page++;
            render();
        }
        // Menu en lecture seule sinon : les recompenses sont deja donnees automatiquement.
    }
}
