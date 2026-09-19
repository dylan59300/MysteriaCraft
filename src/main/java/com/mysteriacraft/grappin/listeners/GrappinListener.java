package com.mysteriacraft.grappin.listeners;

import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.grappin.GrappinManager;
import com.mysteriacraft.grappin.GrappinService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Clic-droit (dans le vide ou sur un bloc) avec le Grappin en main : declenche la traction. */
public class GrappinListener implements Listener {

    private final GrappinManager manager;
    private final GrappinService service;
    private final CustomItemManager customItemManager;

    public GrappinListener(GrappinManager manager, GrappinService service, CustomItemManager customItemManager) {
        this.manager = manager;
        this.service = service;
        this.customItemManager = customItemManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        String customItemId = customItemManager.getCustomItemId(item);
        if (customItemId == null || !customItemId.equalsIgnoreCase(manager.getItemId())) {
            return;
        }
        event.setCancelled(true);
        service.use(event.getPlayer());
    }
}
