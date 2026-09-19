package com.mysteriacraft.dashboard.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.customitems.generator.GeneratorManager;
import com.mysteriacraft.customitems.miningmachine.MiningMachineManager;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.generator.LuckyBlockGeneratorManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu recapitulatif en LECTURE SEULE listant TOUS les blocs "actifs" possedes par le joueur
 * (Generateurs d'argent/ressources, Generateur de Lucky Block, Machine a Miner), pour eviter de
 * jongler entre /generateur liste, /generateurlb liste et /machineminiere info. La Machine a
 * Transformation n'a pas de notion de proprietaire (utilisable par n'importe quel joueur) : elle
 * n'apparait donc pas ici.
 */
public class MesMachinesGui extends Menu {

    private static final int SIZE = 54;
    private static final int CONTENT_SIZE = 36;
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;

    private final GeneratorManager generatorManager;
    private final LuckyBlockGeneratorManager luckyBlockGeneratorManager;
    private final MiningMachineManager miningMachineManager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    private Inventory inventory;
    private List<ItemStack> entries;
    private int page = 0;

    public MesMachinesGui(Player viewer, GeneratorManager generatorManager,
                           LuckyBlockGeneratorManager luckyBlockGeneratorManager,
                           MiningMachineManager miningMachineManager,
                           EconomyManager economyManager, MessageManager messages) {
        super(viewer);
        this.generatorManager = generatorManager;
        this.luckyBlockGeneratorManager = luckyBlockGeneratorManager;
        this.miningMachineManager = miningMachineManager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("dashboard.titre-gui")));
        holder.setInventory(inventory);
        this.entries = collectEntries();
        render();
        return inventory;
    }

    private List<ItemStack> collectEntries() {
        List<ItemStack> list = new ArrayList<>();

        for (Location location : generatorManager.getActiveGeneratorLocations()) {
            Block block = location.getBlock();
            if (!generatorManager.isGeneratorBlock(block) || !viewer.getUniqueId().equals(generatorManager.getOwner(block))) {
                continue;
            }
            GeneratorManager.GeneratorType type = generatorManager.getBlockType(block);
            if (type == null) {
                continue;
            }
            double stored = generatorManager.accrue(block);
            double max = generatorManager.getEffectiveStorageMax(block);
            String storedText = type.producesItems()
                    ? (int) Math.floor(stored) + " / " + (int) max
                    : economyManager.format(stored) + " / " + economyManager.format(max);
            list.add(new ItemBuilder(type.block())
                    .name("&e" + type.displayName())
                    .lore(List.of("&7" + coords(location), "&7Stock : &a" + storedText))
                    .build());
        }

        for (Location location : luckyBlockGeneratorManager.getActiveGeneratorLocations()) {
            Block block = location.getBlock();
            if (!luckyBlockGeneratorManager.isGeneratorBlock(block)
                    || !viewer.getUniqueId().equals(luckyBlockGeneratorManager.getOwner(block))) {
                continue;
            }
            LuckyBlockFamily family = luckyBlockGeneratorManager.getFamily(block);
            String name = family != null ? family.displayName() : "?";
            int fuel = luckyBlockGeneratorManager.getFuel(block);
            int max = luckyBlockGeneratorManager.getEffectiveFuelMax(block);
            list.add(new ItemBuilder(Material.BEACON)
                    .name("&dGenerateur de Lucky Block &7[" + name + "&7]")
                    .lore(List.of("&7" + coords(location), "&7Carburant : &b" + fuel + "&7/&b" + max))
                    .build());
        }

        for (Location location : miningMachineManager.getActiveMachineLocations()) {
            Block block = location.getBlock();
            if (!miningMachineManager.isMachineBlock(block)
                    || !viewer.getUniqueId().equals(miningMachineManager.getOwner(block))) {
                continue;
            }
            boolean active = miningMachineManager.isActive(block);
            String progress = active
                    ? miningMachineManager.getMinedBlocks(block) + "/" + miningMachineManager.getTotalBlocks(block)
                    : "a l'arret";
            list.add(new ItemBuilder(Material.NETHERITE_PICKAXE)
                    .name("&bMachine a Miner")
                    .lore(List.of(
                            "&7" + coords(location),
                            "&7Carburant : &b" + miningMachineManager.getFuel(block),
                            "&7Progression : &e" + progress))
                    .build());
        }

        return list;
    }

    private String coords(Location location) {
        return (location.getWorld() != null ? location.getWorld().getName() : "?")
                + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(entries.size() / (double) CONTENT_SIZE));
    }

    private void render() {
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = CONTENT_SIZE; i < SIZE; i++) {
            inventory.setItem(i, border);
        }
        for (int i = 0; i < CONTENT_SIZE; i++) {
            inventory.setItem(i, null);
        }

        int firstIndex = page * CONTENT_SIZE;
        for (int i = 0; i < CONTENT_SIZE; i++) {
            int index = firstIndex + i;
            if (index >= entries.size()) {
                break;
            }
            inventory.setItem(i, entries.get(index));
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
        }
    }
}
