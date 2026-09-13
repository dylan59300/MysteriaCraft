package com.mysteriacraft.customitems.listeners;

import com.mysteriacraft.customitems.CustomItemService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class CustomItemListener implements Listener {

    private final CustomItemService service;

    public CustomItemListener(CustomItemService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        service.handleOreBreak(event);
    }
}
