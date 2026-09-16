package com.mysteriacraft.etabli.listeners;

import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.etabli.EtabliManager;
import com.mysteriacraft.etabli.EtabliService;
import com.mysteriacraft.etabli.gui.EtabliGui;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Clic-droit sur le bloc d'Etabli : avec un parchemin en main -> decouvre sa recette ; sinon ->
 * ouvre le menu des recettes. */
public class EtabliListener implements Listener {

    private final Plugin plugin;
    private final EtabliManager manager;
    private final EtabliService service;
    private final CustomItemManager customItemManager;
    private final MessageManager messages;

    public EtabliListener(Plugin plugin, EtabliManager manager, EtabliService service,
                           CustomItemManager customItemManager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.service = service;
        this.customItemManager = customItemManager;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !manager.isEtabliBlock(block.getType())) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String customId = customItemManager.getCustomItemId(inHand);
        if (manager.getRecetteByParchemin(customId) != null) {
            service.deverrouiller(player);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> unlocked = manager.getUnlockedRecetteIds(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    new EtabliGui(player, manager, service, messages, unlocked).open();
                }
            });
        });
    }
}
