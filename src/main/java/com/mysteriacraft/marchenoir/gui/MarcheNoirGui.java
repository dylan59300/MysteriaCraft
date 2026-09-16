package com.mysteriacraft.marchenoir.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.marchenoir.MarcheNoirManager;
import com.mysteriacraft.marchenoir.MarcheNoirService;
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

/** Menu du Marche Noir : affiche la selection actuelle (identique pour tous jusqu'a la prochaine
 * rotation) avec son prix et le temps restant avant renouvellement. */
public class MarcheNoirGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final MarcheNoirManager manager;
    private final MarcheNoirService service;
    private final EconomyManager economyManager;
    private final MessageManager messages;
    private final Map<Integer, String> slotToOffreId = new HashMap<>();

    private Inventory inventory;

    public MarcheNoirGui(Player viewer, MarcheNoirManager manager, MarcheNoirService service,
                          EconomyManager economyManager, MessageManager messages) {
        super(viewer);
        this.manager = manager;
        this.service = service;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("marchenoir.titre-gui")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        slotToOffreId.clear();
        ItemStack border = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        List<MarcheNoirManager.OffreActive> offres = manager.getOffresActuelles();
        for (int i = 0; i < ITEM_SLOTS.length && i < offres.size(); i++) {
            MarcheNoirManager.OffreActive offre = offres.get(i);
            inventory.setItem(ITEM_SLOTS[i], buildIcon(offre));
            slotToOffreId.put(ITEM_SLOTS[i], offre.id());
        }

        long remainingMillis = manager.getMillisAvantRotation();
        inventory.setItem(22, new ItemBuilder(Material.CLOCK)
                .name(messages.raw("marchenoir.gui-prochaine-rotation"))
                .lore(List.of(replace(messages.raw("marchenoir.gui-temps-restant"), "temps", formatDuration(remainingMillis))))
                .build());
    }

    private ItemStack buildIcon(MarcheNoirManager.OffreActive offre) {
        ItemStack icon = offre.reward().displayIcon() != null ? offre.reward().displayIcon().clone() : new ItemStack(Material.CHEST);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>(meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of());
            lore.add("");
            lore.add(replace(messages.raw("marchenoir.gui-prix"), "prix", economyManager.format(offre.prix())));
            lore.add(messages.raw("marchenoir.gui-clic-acheter"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }

    private String formatDuration(long millis) {
        long totalMinutes = Math.max(0, millis / 60_000);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours + "h" + (minutes < 10 ? "0" : "") + minutes;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        String offreId = slotToOffreId.get(event.getSlot());
        if (offreId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        service.acheter(player, offreId);
        render();
    }
}
