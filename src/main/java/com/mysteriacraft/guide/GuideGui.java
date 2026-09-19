package com.mysteriacraft.guide;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu en lecture seule (/guide) presentant les principaux sujets/mecaniques du serveur, en
 * grille paginee. S'ouvre a la demande ou automatiquement a la premiere connexion d'un joueur
 * (voir GuideJoinListener).
 */
public class GuideGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] TOPIC_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;

    private final GuideManager manager;
    private final MessageManager messages;

    private Inventory inventory;
    private List<GuideManager.GuideTopic> topics;
    private int page = 0;

    public GuideGui(Player viewer, GuideManager manager, MessageManager messages) {
        super(viewer);
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("guide.titre-gui")));
        holder.setInventory(inventory);
        this.topics = manager.getTopics();
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(topics.size() / (double) TOPIC_SLOTS.length));
    }

    private void render() {
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int firstIndex = page * TOPIC_SLOTS.length;
        for (int i = 0; i < TOPIC_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= topics.size()) {
                break;
            }
            GuideManager.GuideTopic topic = topics.get(index);
            List<String> lore = new ArrayList<>(topic.lore());
            inventory.setItem(TOPIC_SLOTS[i], new ItemBuilder(topic.icon()).name(topic.displayName()).lore(lore).build());
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
        }
        // Menu en lecture seule sinon : les autres clics sont ignores.
    }
}
