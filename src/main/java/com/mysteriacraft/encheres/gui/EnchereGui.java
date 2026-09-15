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
 * Menu de l'Hotel des Ventes : liste TOUTES les annonces actives (icone = l'item REEL en vente),
 * clic pour acheter (voir EnchereService#acheter). Toujours rechargee depuis la base a
 * l'ouverture (les annonces changent frequemment, contrairement aux autres menus statiques).
 */
public class EnchereGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] ANNONCE_SLOTS = buildAnnonceSlots();
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;

    private final Plugin plugin;
    private final EnchereManager manager;
    private final EnchereService service;
    private final MessageManager messages;

    private Inventory inventory;
    private List<Annonce> annonces;
    private int page = 0;
    private final Map<Integer, Integer> slotToAnnonceId = new HashMap<>();

    public EnchereGui(Plugin plugin, Player viewer, EnchereManager manager, EnchereService service, MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    private static int[] buildAnnonceSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("encheres.titre-gui")));
        holder.setInventory(inventory);
        this.annonces = manager.getAnnoncesActives();
        render();
        return inventory;
    }

    /** Recharge les annonces depuis la base (async) puis rouvre le menu a la meme page. */
    public void refresh() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Annonce> fresh = manager.getAnnoncesActives();
            Bukkit.getScheduler().runTask(plugin, () -> {
                this.annonces = fresh;
                open();
            });
        });
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(annonces.size() / (double) ANNONCE_SLOTS.length));
    }

    private void render() {
        slotToAnnonceId.clear();
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int firstIndex = page * ANNONCE_SLOTS.length;
        for (int i = 0; i < ANNONCE_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= annonces.size()) {
                break;
            }
            Annonce annonce = annonces.get(index);
            inventory.setItem(ANNONCE_SLOTS[i], buildAnnonceItem(annonce));
            slotToAnnonceId.put(ANNONCE_SLOTS[i], annonce.id());
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

    private ItemStack buildAnnonceItem(Annonce annonce) {
        ItemStack display = annonce.item().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(replace(messages.raw("encheres.gui-vendeur"), "vendeur", annonce.vendeurNom()));
            lore.add(replace(messages.raw("encheres.gui-prix"), "prix", String.valueOf((long) annonce.prix())));
            lore.add(messages.raw("encheres.gui-cliquer-acheter"));
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
        int slot = event.getSlot();
        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == NEXT_SLOT && page < maxPage() - 1) {
            page++;
            render();
            return;
        }

        Integer annonceId = slotToAnnonceId.get(slot);
        if (annonceId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        service.acheter(player, annonceId);
        refresh();
    }
}
