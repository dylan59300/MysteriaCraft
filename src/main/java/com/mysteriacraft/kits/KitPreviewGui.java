package com.mysteriacraft.kits;

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

/**
 * Menu en lecture seule montrant le contenu reel d'un kit (clic droit depuis KitGui).
 * Un clic sur le bouton "retour" rouvre le menu des kits a la meme page.
 */
public class KitPreviewGui extends Menu {

    private static final int SIZE = 27;
    private static final int BACK_SLOT = 22;

    private final Kit kit;
    private final KitGui parent;
    private final MessageManager messages;

    public KitPreviewGui(Player viewer, Kit kit, KitGui parent, MessageManager messages) {
        super(viewer);
        this.kit = kit;
        this.parent = parent;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        String title = MessageManager.color(messages.raw("kits.gui-apercu-titre")) + " : " + MessageManager.color(kit.displayName());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int slot = 9;
        for (ItemStack item : kit.items()) {
            if (slot >= SIZE) {
                break; // Kit avec plus d'objets que le menu ne peut en montrer d'un coup.
            }
            if (slot == BACK_SLOT) {
                slot++;
            }
            inventory.setItem(slot, item.clone());
            slot++;
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name(messages.raw("kits.gui-apercu-retour")).build());

        return inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == BACK_SLOT && event.getWhoClicked() instanceof Player) {
            parent.open();
        }
        // Tous les autres clics sont ignores : menu en lecture seule.
    }
}
