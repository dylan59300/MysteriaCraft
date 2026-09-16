package com.mysteriacraft.marchand.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.marchand.MarchandDefinition;
import com.mysteriacraft.marchand.MarchandRachat;
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
 * Menu "Vendre des ressources" d'un PNJ Marchand : sens inverse de MarchandGui, liste les rachats
 * (voir MarchandRachat) proposes par ce marchand. Vente au clic si le joueur possede assez
 * d'exemplaires du materiel demande et n'a pas atteint la limite de periode.
 */
public class MarchandRachatsGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] RACHAT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PREVIOUS_SLOT = 18;
    private static final int NEXT_SLOT = 26;
    private static final int RETOUR_SLOT = 22;

    private final MarchandDefinition definition;
    private final MarchandService service;
    private final MessageManager messages;

    private Inventory inventory;
    private List<MarchandRachat> rachats;
    private int page = 0;
    private final Map<Integer, MarchandRachat> slotToRachat = new HashMap<>();

    public MarchandRachatsGui(Player viewer, MarchandDefinition definition, MarchandService service, MessageManager messages) {
        super(viewer);
        this.definition = definition;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        String title = MessageManager.color(messages.raw("marchand.titre-gui-rachats").replace("{nom}", definition.npcName()));
        this.inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);
        this.rachats = definition.rachats();
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(rachats.size() / (double) RACHAT_SLOTS.length));
    }

    private void render() {
        slotToRachat.clear();

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int firstIndex = page * RACHAT_SLOTS.length;
        for (int i = 0; i < RACHAT_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= rachats.size()) {
                break;
            }
            MarchandRachat rachat = rachats.get(index);
            inventory.setItem(RACHAT_SLOTS[i], buildRachatItem(rachat));
            slotToRachat.put(RACHAT_SLOTS[i], rachat);
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
        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("marchand.gui-retour")).build());
    }

    private ItemStack buildRachatItem(MarchandRachat rachat) {
        int possede = countMateriel(rachat);
        boolean hasEnough = possede >= rachat.quantite();

        int done = rachat.isLimited() ? service.countRachatsThisPeriod(viewer.getUniqueId(), definition, rachat) : 0;
        boolean limitReached = rachat.isLimited() && done >= rachat.limiteQuantite();

        List<String> lore = new ArrayList<>();
        lore.add(replace(messages.raw("marchand.gui-vous-possedez"), "quantite", String.valueOf(possede), "requis", String.valueOf(rachat.quantite())));
        if (rachat.recompense() != null) {
            lore.add(replace(messages.raw("marchand.gui-recompense"), "recompense", rachat.recompense().displayName()));
        }
        if (rachat.isLimited()) {
            lore.add(replace(messages.raw("marchand.gui-limite"), "fait", String.valueOf(done), "max", String.valueOf(rachat.limiteQuantite())));
        }
        lore.add("");
        if (limitReached) {
            lore.add(messages.raw("marchand.gui-limite-atteinte"));
        } else if (hasEnough) {
            lore.add(messages.raw("marchand.gui-cliquer-vendre"));
        } else {
            lore.add(messages.raw("marchand.gui-objets-insuffisants"));
        }

        boolean available = hasEnough && !limitReached;
        Material material = available ? rachat.icon().getType() : Material.GRAY_DYE;
        String name = available ? rachat.displayName() : "&7" + rachat.displayName();

        return new ItemBuilder(material).name(name).lore(lore).build();
    }

    private int countMateriel(MarchandRachat rachat) {
        int total = 0;
        for (ItemStack item : viewer.getInventory().getContents()) {
            if (item != null && item.getType() == rachat.materiel()) {
                total += item.getAmount();
            }
        }
        return total;
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
        if (slot == RETOUR_SLOT && event.getWhoClicked() instanceof Player player) {
            new MarchandGui(player, definition, service, messages).open();
            return;
        }

        MarchandRachat rachat = slotToRachat.get(slot);
        if (rachat == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        service.rachat(player, definition, rachat);
        render();
    }
}
