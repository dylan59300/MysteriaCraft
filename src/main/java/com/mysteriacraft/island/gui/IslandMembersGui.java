package com.mysteriacraft.island.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.island.IslandManager;
import com.mysteriacraft.island.IslandService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menu (/ile membres) listant les membres de confiance de SA PROPRE ile, avec un clic pour
 * exclure. Ouvert uniquement au proprietaire (verifie par IslandCommand).
 */
public class IslandMembersGui extends Menu {

    private static final int SIZE = 45;

    private final IslandManager.Island island;
    private final IslandService service;
    private final MessageManager messages;
    private final Map<Integer, UUID> slotToMember = new HashMap<>();

    private Inventory inventory;

    public IslandMembersGui(Player viewer, IslandManager.Island island, IslandService service, MessageManager messages) {
        super(viewer);
        this.island = island;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("ile.gui-membres-titre")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        slotToMember.clear();
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        List<UUID> members = new ArrayList<>(island.members());
        for (int i = 0; i < members.size() && i < SIZE; i++) {
            UUID memberId = members.get(i);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
            String name = offline.getName() != null ? offline.getName() : memberId.toString();

            List<String> lore = List.of(messages.raw("ile.gui-membres-clic-exclure"));
            inventory.setItem(i, new ItemBuilder(Material.PLAYER_HEAD).name("&e" + name).lore(lore).build());
            slotToMember.put(i, memberId);
        }

        if (members.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(messages.raw("ile.gui-membres-aucun")).build());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        UUID memberId = slotToMember.get(event.getSlot());
        if (memberId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
        if (service.kickMember(player, memberId)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("joueur", offline.getName() != null ? offline.getName() : memberId.toString());
            messages.send(player, "ile.membre-exclu", placeholders);
        }
        render();
    }
}
