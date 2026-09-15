package com.mysteriacraft.marchand.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.marchand.MarchandDefinition;
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
 * Menu d'un PNJ Marchand : liste des offres ACTUELLEMENT actives (voir MarchandService#getActiveOffers),
 * avec cout effectif (reduction fidelite deja appliquee) et limite de periode restante. Achat au
 * clic si le joueur a assez de pieces d'echange et n'a pas atteint la limite.
 */
public class MarchandGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] OFFER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PREVIOUS_SLOT = 18;
    private static final int NEXT_SLOT = 26;

    private final MarchandDefinition definition;
    private final MarchandService service;
    private final MessageManager messages;

    private Inventory inventory;
    private List<MarchandOffer> offers;
    private int page = 0;
    private final Map<Integer, MarchandOffer> slotToOffer = new HashMap<>();

    public MarchandGui(Player viewer, MarchandDefinition definition, MarchandService service, MessageManager messages) {
        super(viewer);
        this.definition = definition;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        String title = MessageManager.color(messages.raw("marchand.titre-gui").replace("{nom}", definition.npcName()));
        this.inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);
        this.offers = service.getActiveOffers(definition);
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

        int pieces = service.countPieces(viewer, definition.pieceItemId());

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
        int cost = service.getEffectiveCost(viewer.getUniqueId(), definition, offer);
        boolean canAfford = pieces >= cost;

        int done = offer.isLimited() ? service.countPurchasesThisPeriod(viewer.getUniqueId(), definition, offer) : 0;
        boolean limitReached = offer.isLimited() && done >= offer.limiteQuantite();

        List<String> lore = new ArrayList<>();
        if (cost != offer.cout()) {
            lore.add(replace(messages.raw("marchand.gui-cout-reduit"), "cout", String.valueOf(cost), "cout-base", String.valueOf(offer.cout())));
        } else {
            lore.add(replace(messages.raw("marchand.gui-cout"), "cout", String.valueOf(cost)));
        }
        lore.add(replace(messages.raw("marchand.gui-vos-pieces"), "pieces", String.valueOf(pieces)));
        if (offer.isLimited()) {
            lore.add(replace(messages.raw("marchand.gui-limite"), "fait", String.valueOf(done), "max", String.valueOf(offer.limiteQuantite())));
        }
        lore.add("");
        if (limitReached) {
            lore.add(messages.raw("marchand.gui-limite-atteinte"));
        } else if (canAfford) {
            lore.add(messages.raw("marchand.gui-cliquer-echanger"));
        } else {
            lore.add(messages.raw("marchand.gui-pieces-insuffisantes"));
        }

        boolean available = canAfford && !limitReached;
        Material material = available ? offer.icon().getType() : Material.GRAY_DYE;
        String name = available ? offer.displayName() : "&7" + offer.displayName();

        return new ItemBuilder(material).name(name).lore(lore).build();
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }

    private String replace(String text, String key1, String value1, String key2, String value2) {
        return text.replace("{" + key1 + "}", value1).replace("{" + key2 + "}", value2);
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
        service.purchase(player, definition, offer);
        render();
    }
}
