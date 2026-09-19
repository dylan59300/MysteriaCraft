package com.mysteriacraft.etabli.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.etabli.EtabliManager;
import com.mysteriacraft.etabli.EtabliService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menu des recettes de l'Etabli : les recettes non decouvertes (voir EtabliManager) apparaissent
 * verrouillees ("???"), celles debloquees affichent leurs ingredients et peuvent etre craftees. */
public class EtabliGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] RECETTE_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final EtabliManager manager;
    private final EtabliService service;
    private final MessageManager messages;
    private final List<String> unlockedIds;
    private final Map<Integer, String> slotToRecetteId = new HashMap<>();

    private Inventory inventory;

    public EtabliGui(Player viewer, EtabliManager manager, EtabliService service, MessageManager messages, List<String> unlockedIds) {
        super(viewer);
        this.manager = manager;
        this.service = service;
        this.messages = messages;
        this.unlockedIds = unlockedIds;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("etabli.titre-gui")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        slotToRecetteId.clear();
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        List<EtabliManager.Recette> recettes = manager.getRecettesSorted();
        for (int i = 0; i < RECETTE_SLOTS.length && i < recettes.size(); i++) {
            EtabliManager.Recette recette = recettes.get(i);
            inventory.setItem(RECETTE_SLOTS[i], buildIcon(recette));
            slotToRecetteId.put(RECETTE_SLOTS[i], recette.id());
        }
    }

    private ItemStack buildIcon(EtabliManager.Recette recette) {
        boolean unlocked = unlockedIds.contains(recette.id());
        if (!unlocked) {
            return new ItemBuilder(Material.BARRIER)
                    .name(messages.raw("etabli.gui-verrouillee"))
                    .lore(List.of(messages.raw("etabli.gui-trouver-parchemin")))
                    .build();
        }

        ItemStack icon = recette.resultat().displayIcon() != null ? recette.resultat().displayIcon().clone() : new ItemStack(Material.PAPER);
        List<String> lore = new ArrayList<>();
        lore.add(messages.raw("etabli.gui-ingredients"));
        for (EtabliManager.Ingredient ingredient : recette.ingredients()) {
            String nom = ingredient.customItemId() != null ? ingredient.customItemId() : ingredient.materiel().name().replace('_', ' ');
            lore.add("&7- &f" + ingredient.quantite() + "x " + nom);
        }
        lore.add("");
        lore.add(messages.raw("etabli.gui-clic-craft"));

        List<String> colored = new ArrayList<>();
        for (String line : lore) {
            colored.add(MessageManager.color(line));
        }
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(colored);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        String recetteId = slotToRecetteId.get(event.getSlot());
        if (recetteId == null || !unlockedIds.contains(recetteId) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        service.craft(player, recetteId);
    }
}
