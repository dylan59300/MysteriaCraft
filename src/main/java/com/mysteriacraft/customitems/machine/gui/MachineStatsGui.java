package com.mysteriacraft.customitems.machine.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.customitems.machine.MachineManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu en lecture seule (/machine stats) affichant les statistiques cumulees d'un joueur sur
 * TOUTES ses machines confondues : essais/reussites totaux, taux de reussite reel, et le minerai
 * qu'il a le plus utilise ("minerai favori").
 */
public class MachineStatsGui extends Menu {

    private static final int SIZE = 27;

    private final MachineManager manager;
    private final MessageManager messages;

    public MachineStatsGui(Player viewer, MachineManager manager, MessageManager messages) {
        super(viewer);
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("machine.stats-titre")));
        holder.setInventory(inventory);

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        MachineManager.PlayerStats stats = manager.getPlayerStats(viewer.getUniqueId());
        inventory.setItem(10, buildEssaisIcon(stats));
        inventory.setItem(12, buildTauxIcon(stats));
        inventory.setItem(14, buildFavoriIcon(stats));

        int pityThreshold = manager.getPityThreshold();
        if (pityThreshold > 0) {
            inventory.setItem(16, buildPityIcon(pityThreshold));
        }

        return inventory;
    }

    private ItemStack buildPityIcon(int pityThreshold) {
        int progress = manager.getPityProgress(viewer.getUniqueId());
        List<String> lore = List.of(replace(replace(messages.raw("machine.stats-pity-ligne"),
                "progres", String.valueOf(progress)), "seuil", String.valueOf(pityThreshold)));
        return new ItemBuilder(Material.CLOCK)
                .name(messages.raw("machine.stats-pity-nom"))
                .lore(lore)
                .build();
    }

    private ItemStack buildEssaisIcon(MachineManager.PlayerStats stats) {
        return new ItemBuilder(Material.IRON_BLOCK)
                .name(messages.raw("machine.stats-essais-nom"))
                .lore(List.of(replace(messages.raw("machine.stats-essais-ligne"), "essais", String.valueOf(stats.totalEssais()))))
                .build();
    }

    private ItemStack buildTauxIcon(MachineManager.PlayerStats stats) {
        List<String> lore = new ArrayList<>();
        lore.add(replace(messages.raw("machine.stats-reussites-ligne"), "reussites", String.valueOf(stats.totalReussites())));
        lore.add(replace(messages.raw("machine.stats-taux-ligne"), "taux", String.format("%.1f", stats.tauxReussite())));
        return new ItemBuilder(Material.DIAMOND_BLOCK)
                .name(messages.raw("machine.stats-taux-nom"))
                .lore(lore)
                .build();
    }

    private ItemStack buildFavoriIcon(MachineManager.PlayerStats stats) {
        if (stats.minerauFavori() == null) {
            return new ItemBuilder(Material.BARRIER)
                    .name(messages.raw("machine.stats-favori-nom"))
                    .lore(List.of(messages.raw("machine.stats-favori-aucun")))
                    .build();
        }
        List<String> lore = List.of(replace(messages.raw("machine.stats-favori-ligne"),
                "essais", String.valueOf(stats.essaisMinerauFavori())));
        return new ItemBuilder(stats.minerauFavori())
                .name(messages.raw("machine.stats-favori-nom"))
                .lore(lore)
                .build();
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Menu en lecture seule : aucune action au clic.
    }
}
