package com.mysteriacraft.encheres.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.encheres.Annonce;
import com.mysteriacraft.encheres.EnchereManager;
import com.mysteriacraft.encheres.EnchereService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu listant les annonces ACTIVES du joueur (voir /hoteldesventes mesventes), clic pour
 * annuler et recuperer l'item (voir EnchereService#annuler).
 */
public class EnchereMesVentesGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] ANNONCE_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final Plugin plugin;
    private final EnchereManager manager;
    private final EnchereService service;
    private final MessageManager messages;

    private Inventory inventory;
    private List<Annonce> annonces;
    private final Map<Integer, Integer> slotToAnnonceId = new HashMap<>();

    public EnchereMesVentesGui(Plugin plugin, Player viewer, EnchereManager manager, EnchereService service, MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("encheres.titre-mesventes")));
        holder.setInventory(inventory);
        this.annonces = manager.getAnnoncesDe(viewer.getUniqueId());
        render();
        return inventory;
    }

    public void refresh() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Annonce> fresh = manager.getAnnoncesDe(viewer.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                this.annonces = fresh;
                open();
            });
        });
    }

    private void render() {
        slotToAnnonceId.clear();
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        for (int i = 0; i < ANNONCE_SLOTS.length && i < annonces.size(); i++) {
            Annonce annonce = annonces.get(i);
            inventory.setItem(ANNONCE_SLOTS[i], buildAnnonceItem(annonce));
            slotToAnnonceId.put(ANNONCE_SLOTS[i], annonce.id());
        }
    }

    private ItemStack buildAnnonceItem(Annonce annonce) {
        ItemStack display = annonce.item().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(replace(messages.raw("encheres.gui-prix"), "prix", String.valueOf((long) annonce.prix())));
            lore.add(messages.raw("encheres.gui-cliquer-annuler"));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Integer annonceId = slotToAnnonceId.get(event.getSlot());
        if (annonceId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        service.annuler(player, annonceId);
        refresh();
    }
}
