package com.mysteriacraft.marchand.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.marchand.MarchandManager;
import com.mysteriacraft.marchand.MarchandOffer;
import com.mysteriacraft.marchand.MarchandService;
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
 * Menu du PNJ Marchand : liste des offres, achat au clic si le joueur a assez de pieces
 * d'echange (voir MarchandService#purchase).
 */
public class MarchandGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] OFFER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PREVIOUS_SLOT = 18;
    private static final int NEXT_SLOT = 26;

    private final MarchandManager manager;
    private final MarchandService service;
    private final MessageManager messages;

    private Inventory inventory;
    private List<MarchandOffer> offers;
    private int page = 0;
    private final Map<Integer, MarchandOffer> slotToOffer = new HashMap<>();

    public MarchandGui(Player viewer, MarchandManager manager, MarchandService service, MessageManager messages) {
        super(viewer);
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("marchand.titre-gui")));
        holder.setInventory(inventory);
        this.offers = manager.getOffers();
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(offers.size() / (double) OFFER_SLOTS.length));
    }

    private void render() {
        slotToOffer.clear();

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int pieces = service.countPieces(viewer);

        int firstIndex = page * OFFER_SLOTS.length;
        for (int i = 0; i < OFFER_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= offers.size()) {
                break;
            }
            MarchandOffer offer = offers.get(index);
            inventory.setItem(OFFER_SLOTS[i], buildOfferItem(offer, pieces));
            slotToOffer.put(OFFER_SLOTS[i], offer);
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

    private ItemStack buildOfferItem(MarchandOffer offer, int pieces) {
        boolean canAfford = pieces >= offer.cout();

        List<String> lore = new ArrayList<>();
        lore.add(replace(messages.raw("marchand.gui-cout"), "cout", String.valueOf(offer.cout())));
        lore.add(replace(messages.raw("marchand.gui-vos-pieces"), "pieces", String.valueOf(pieces)));
        lore.add("");
        lore.add(canAfford ? messages.raw("marchand.gui-cliquer-echanger") : messages.raw("marchand.gui-pieces-insuffisantes"));

        Material material = canAfford ? offer.icon().getType() : Material.GRAY_DYE;
        String name = canAfford ? offer.displayName() : "&7" + offer.displayName();

        return new ItemBuilder(material).name(name).lore(lore).build();
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

        MarchandOffer offer = slotToOffer.get(slot);
        if (offer == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        service.purchase(player, offer);
        render();
    }
}
