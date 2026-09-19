package com.mysteriacraft.voucher.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.voucher.VoucherDefinition;
import com.mysteriacraft.voucher.VoucherManager;
import com.mysteriacraft.voucher.VoucherService;
import com.mysteriacraft.voucher.gui.VoucherConfirmGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Clic-droit avec un Voucher en main principale : ouvre le menu de confirmation (voir
 * VoucherConfirmGui/VoucherService#redeem). */
public class VoucherListener implements Listener {

    private final VoucherManager manager;
    private final VoucherService service;
    private final MessageManager messages;

    public VoucherListener(VoucherManager manager, VoucherService service, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String voucherId = service.getVoucherId(inHand);
        if (voucherId == null) {
            return;
        }
        VoucherDefinition definition = manager.getVoucher(voucherId);
        if (definition == null) {
            messages.send(player, "voucher.introuvable");
            return;
        }
        event.setCancelled(true);
        new VoucherConfirmGui(player, service, definition, messages).open();
    }
}
