package com.mysteriacraft.classes.gui;

import com.mysteriacraft.classes.ClasseDefinition;
import com.mysteriacraft.classes.ClasseManager;
import com.mysteriacraft.classes.ClasseService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu simple listant les classes/metiers disponibles, clic pour choisir (voir ClasseService#choisir).
 */
public class ClasseGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] CLASSE_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final ClasseManager manager;
    private final ClasseService service;
    private final MessageManager messages;
    private final String classeActive;
    private final Map<Integer, String> slotToClasse = new HashMap<>();

    public ClasseGui(Player viewer, ClasseManager manager, ClasseService service, MessageManager messages, String classeActive) {
        super(viewer);
        this.manager = manager;
        this.service = service;
        this.messages = messages;
        this.classeActive = classeActive;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("classes.titre-gui")));
        holder.setInventory(inventory);

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        List<ClasseDefinition> classes = manager.getClasses();
        for (int i = 0; i < CLASSE_SLOTS.length && i < classes.size(); i++) {
            ClasseDefinition classe = classes.get(i);
            boolean active = classe.id().equalsIgnoreCase(classeActive);

            List<String> lore = new ArrayList<>(classe.lore());
            lore.add("");
            if (active) {
                lore.add(messages.raw("classes.gui-active"));
            } else if (classeActive == null) {
                lore.add(messages.raw("classes.gui-cliquer-gratuit"));
            } else {
                lore.add(replace(messages.raw("classes.gui-cliquer-payant"), "prix", String.valueOf((long) manager.getPrixChangement())));
            }

            inventory.setItem(CLASSE_SLOTS[i], new ItemBuilder(classe.icon())
                    .name(active ? "&a&l" + stripColor(classe.displayName()) : classe.displayName())
                    .lore(lore).build());
            slotToClasse.put(CLASSE_SLOTS[i], classe.id());
        }

        return inventory;
    }

    private String stripColor(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "");
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        String classeId = slotToClasse.get(event.getSlot());
        if (classeId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        player.closeInventory();
        service.choisir(player, classeId);
    }
}
