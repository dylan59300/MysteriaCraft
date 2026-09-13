package com.mysteriacraft.crates.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.crates.Crate;
import com.mysteriacraft.crates.CrateManager;
import com.mysteriacraft.crates.CrateReward;
import com.mysteriacraft.crates.CrateService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Animation "reveal rapide" : l'icone centrale change tres vite parmi les recompenses possibles
 * puis se fixe sur le gain reel, façon "loot box" rapide.
 */
public class QuickRevealCrateGui extends Menu {

    private static final int SIZE = 27;
    private static final int RESULT_SLOT = 13;
    private static final int CLOSE_SLOT = 22;
    private static final int TOTAL_TICKS = 40; // ~2 secondes a 20 TPS
    private static final long PERIOD_TICKS = 2L;

    private final Plugin plugin;
    private final Crate crate;
    private final CrateReward reward;
    private final CrateManager crateManager;
    private final CrateService crateService;
    private final MessageManager messages;

    private Inventory inventory;
    private BukkitTask task;
    private boolean finished = false;

    public QuickRevealCrateGui(Plugin plugin, Player viewer, Crate crate, CrateReward reward,
                                CrateManager crateManager, CrateService crateService, MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
        this.crate = crate;
        this.reward = reward;
        this.crateManager = crateManager;
        this.crateService = crateService;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(crate.displayName()));
        holder.setInventory(inventory);

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }
        inventory.setItem(RESULT_SLOT, new ItemBuilder(Material.ITEM_FRAME)
                .name(messages.raw("crates.gui-tirage-en-cours")).build());

        startAnimation();
        return inventory;
    }

    private void startAnimation() {
        task = new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                if (ticksElapsed >= TOTAL_TICKS) {
                    reveal();
                    cancel();
                    return;
                }
                CrateReward randomFlicker = crateManager.pickReward(crate);
                if (randomFlicker != null) {
                    inventory.setItem(RESULT_SLOT, randomFlicker.displayIcon().clone());
                }
                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, PERIOD_TICKS);
    }

    private void reveal() {
        finished = true;
        ItemStack finalIcon = reward.displayIcon().clone();
        ItemMeta meta = finalIcon.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(MessageManager.color(messages.raw("crates.gui-gagne")));
            meta.setLore(lore);
            finalIcon.setItemMeta(meta);
        }
        inventory.setItem(RESULT_SLOT, finalIcon);
        inventory.setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("crates.gui-fermer")).build());

        crateService.giveReward(viewer, reward);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (finished && event.getWhoClicked() instanceof Player player) {
            player.closeInventory();
        }
        // Pendant l'animation, tous les clics sont ignores (deja annules par le MenuListener).
    }

    @Override
    public void handleClose() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        // Si le joueur ferme avant la fin de l'animation, la recompense a deja ete tiree en amont
        // (CrateService.open) : on la lui donne quand meme pour ne pas perdre la cle consommee.
        if (!finished) {
            crateService.giveReward(viewer, reward);
        }
    }
}
