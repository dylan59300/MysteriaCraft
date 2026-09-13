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
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Animation "roulette" : un bandeau d'icones defile dans la rangee du milieu et ralentit
 * progressivement (deceleration) jusqu'a s'arreter sur la recompense reelle, au centre,
 * entre les deux fleches indicatrices (façon ouverture de caisse "case opening").
 */
public class RouletteCrateGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] REEL_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int CENTER_OFFSET = 4; // slot 13, au milieu de REEL_SLOTS
    private static final int TOP_POINTER_SLOT = 4;
    private static final int BOTTOM_POINTER_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    private static final int FINAL_STEP = 30;
    private static final long MIN_DELAY = 1L;
    private static final long MAX_DELAY = 9L;

    private final Plugin plugin;
    private final Crate crate;
    private final CrateReward reward;
    private final CrateManager crateManager;
    private final CrateService crateService;
    private final MessageManager messages;

    private Inventory inventory;
    private List<CrateReward> strip;
    private BukkitTask pendingTask;
    private boolean cancelled = false;
    private boolean finished = false;

    public RouletteCrateGui(Plugin plugin, Player viewer, Crate crate, CrateReward reward,
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
        inventory.setItem(TOP_POINTER_SLOT, new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE).name("&e▼").build());
        inventory.setItem(BOTTOM_POINTER_SLOT, new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE).name("&e▲").build());

        this.strip = buildStrip();
        animateStep(0);
        return inventory;
    }

    /** Genere le bandeau : des recompenses aleatoires, avec la vraie recompense placee pile au point d'arret. */
    private List<CrateReward> buildStrip() {
        int winningIndex = FINAL_STEP + CENTER_OFFSET;
        int length = winningIndex + REEL_SLOTS.length - CENTER_OFFSET;
        List<CrateReward> generated = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            if (i == winningIndex) {
                generated.add(reward);
            } else {
                CrateReward filler = crateManager.pickReward(crate);
                generated.add(filler != null ? filler : reward);
            }
        }
        return generated;
    }

    private void displayStep(int step) {
        for (int i = 0; i < REEL_SLOTS.length; i++) {
            int index = step + i;
            CrateReward entry = index < strip.size() ? strip.get(index) : reward;
            inventory.setItem(REEL_SLOTS[i], entry.displayIcon().clone());
        }
    }

    private void animateStep(int step) {
        if (cancelled) {
            return;
        }
        displayStep(step);

        if (step >= FINAL_STEP) {
            finishReveal();
            return;
        }

        long delay = computeDelay(step);
        pendingTask = Bukkit.getScheduler().runTaskLater(plugin, () -> animateStep(step + 1), delay);
    }

    /** Deceleration en "ease-out cubique" : rapide au debut, tres lent juste avant l'arret. */
    private long computeDelay(int step) {
        double progress = step / (double) FINAL_STEP;
        double eased = Math.pow(progress, 3);
        return MIN_DELAY + Math.round(eased * (MAX_DELAY - MIN_DELAY));
    }

    private void finishReveal() {
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
        inventory.setItem(REEL_SLOTS[CENTER_OFFSET], finalIcon);
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
        cancelled = true;
        if (pendingTask != null && !pendingTask.isCancelled()) {
            pendingTask.cancel();
        }
        // Si le joueur ferme avant la fin de l'animation, la recompense a deja ete tiree en amont
        // (CrateService.open) : on la lui donne quand meme pour ne pas perdre la cle consommee.
        if (!finished) {
            crateService.giveReward(viewer, reward);
        }
    }
}
