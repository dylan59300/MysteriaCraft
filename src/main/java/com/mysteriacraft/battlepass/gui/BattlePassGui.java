package com.mysteriacraft.battlepass.gui;

import com.mysteriacraft.battlepass.BattlePassLevel;
import com.mysteriacraft.battlepass.BattlePassManager;
import com.mysteriacraft.battlepass.BattlePassReward;
import com.mysteriacraft.battlepass.BattlePassService;
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
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Menu du BattlePass : deux pistes de recompenses (gratuite en haut, premium en bas) alignees
 * par palier, pagination, achat de la piste premium, et reclamation au clic.
 * Saison permanente : la progression (xp, premium, reclamations) n'est jamais reinitialisee.
 */
public class BattlePassGui extends Menu {

    private static final int SIZE = 54;
    private static final int[] FREE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] LEVEL_LABEL_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] PREMIUM_SLOTS = {28, 29, 30, 31, 32, 33, 34};
    private static final int INFO_SLOT = 4;
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int BUY_PREMIUM_SLOT = 49;

    private final Plugin plugin;
    private final BattlePassManager manager;
    private final BattlePassService service;
    private final MessageManager messages;

    private long xp;
    private boolean premium;
    private Set<String> claims;

    private Inventory inventory;
    private List<BattlePassLevel> levels;
    private int page = 0;
    private final Map<Integer, int[]> slotToLevelAndTrack = new HashMap<>(); // slot -> {niveau, 0=gratuit/1=premium}

    public BattlePassGui(Plugin plugin, Player viewer, BattlePassManager manager, BattlePassService service,
                          MessageManager messages, long xp, boolean premium, Set<String> claims) {
        super(viewer);
        this.plugin = plugin;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
        this.xp = xp;
        this.premium = premium;
        this.claims = claims;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("battlepass.titre-gui")));
        holder.setInventory(inventory);
        this.levels = manager.getLevels();
        render();
        return inventory;
    }

    /** Recharge xp/premium/reclamations depuis la base (async) puis rouvre le menu a la meme page. */
    public void refresh() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long newXp = manager.getXp(viewer.getUniqueId());
            boolean newPremium = service.isPremiumEffective(viewer);
            Set<String> newClaims = manager.getAllClaims(viewer.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                this.xp = newXp;
                this.premium = newPremium;
                this.claims = newClaims;
                open();
            });
        });
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(levels.size() / (double) FREE_SLOTS.length));
    }

    private void render() {
        slotToLevelAndTrack.clear();

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int currentLevel = manager.computeLevel(xp);
        renderInfoItem(currentLevel);

        int firstIndex = page * FREE_SLOTS.length;
        for (int i = 0; i < FREE_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= levels.size()) {
                break;
            }
            BattlePassLevel level = levels.get(index);
            boolean unlocked = level.level() <= currentLevel;

            inventory.setItem(LEVEL_LABEL_SLOTS[i], buildLevelLabel(level));
            inventory.setItem(FREE_SLOTS[i], buildRewardItem(level, level.freeReward(), unlocked, false));
            slotToLevelAndTrack.put(FREE_SLOTS[i], new int[]{level.level(), 0});

            inventory.setItem(PREMIUM_SLOTS[i], buildRewardItem(level, level.premiumReward(), unlocked, true));
            slotToLevelAndTrack.put(PREMIUM_SLOTS[i], new int[]{level.level(), 1});
        }

        int maxPage = maxPage();
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW)
                    .name(messages.raw("kits.gui-page-precedente")).build());
        }
        if (page < maxPage - 1) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW)
                    .name(messages.raw("kits.gui-page-suivante")).build());
        }

        if (!premium) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("prix", String.valueOf((long) manager.getPremiumPrice()) + "$");
            String name = messages.raw("battlepass.gui-acheter-premium");
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                name = name.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            inventory.setItem(BUY_PREMIUM_SLOT, new ItemBuilder(Material.NETHER_STAR)
                    .name(name)
                    .lore(List.of(messages.raw("battlepass.gui-cliquer-acheter")))
                    .build());
        }
    }

    private void renderInfoItem(int currentLevel) {
        BattlePassLevel next = manager.getLevel(currentLevel + 1);
        List<String> lore = new ArrayList<>();
        lore.add(replace(messages.raw("battlepass.gui-info-xp"), "xp", String.valueOf(xp)));
        if (next != null) {
            long remaining = Math.max(0, next.xpRequired() - xp);
            lore.add(replace(messages.raw("battlepass.gui-info-suivant"), "xp", String.valueOf(remaining)));
        } else {
            lore.add(messages.raw("battlepass.gui-info-max"));
        }
        lore.add(messages.raw(premium ? "battlepass.gui-info-premium-actif" : "battlepass.gui-info-premium-inactif"));

        String name = replace(messages.raw("battlepass.gui-info-niveau"), "niveau", String.valueOf(currentLevel));
        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.NETHER_STAR).name(name).lore(lore).build());
    }

    private ItemStack buildLevelLabel(BattlePassLevel level) {
        String name = replace(messages.raw("battlepass.gui-niveau-label"), "niveau", String.valueOf(level.level()));
        List<String> lore = List.of(replace(messages.raw("battlepass.gui-niveau-xp-requis"), "xp", String.valueOf(level.xpRequired())));
        return new ItemBuilder(Material.PAPER).name(name).lore(lore).build();
    }

    private ItemStack buildRewardItem(BattlePassLevel level, BattlePassReward reward, boolean unlocked, boolean premiumTrack) {
        if (reward == null) {
            return new ItemBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name(messages.raw("battlepass.gui-aucune-recompense")).build();
        }

        String trackKey = premiumTrack ? BattlePassService.TRACK_PREMIUM : BattlePassService.TRACK_FREE;
        boolean claimed = claims.contains(level.level() + "#" + trackKey);
        boolean needsPremium = premiumTrack && !premium;

        List<String> lore = new ArrayList<>();
        lore.add("");
        if (claimed) {
            lore.add(messages.raw("battlepass.gui-deja-reclame"));
        } else if (!unlocked) {
            lore.add(messages.raw("battlepass.gui-verrouille"));
        } else if (needsPremium) {
            lore.add(messages.raw("battlepass.gui-besoin-premium"));
        } else {
            lore.add(messages.raw("battlepass.gui-cliquer-reclamer"));
        }

        Material material = (claimed || !unlocked || needsPremium) ? Material.GRAY_DYE : reward.displayIcon().getType();
        String name = (claimed || !unlocked || needsPremium) ? "&7" + reward.displayName() : reward.displayName();

        return new ItemBuilder(material).name(name).lore(lore).build();
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
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
            return;
        }
        if (slot == BUY_PREMIUM_SLOT && !premium && event.getWhoClicked() instanceof Player player) {
            // L'achat necessite une confirmation cliquable dans le chat : on ferme le menu
            // pour que le joueur voie clairement le message de confirmation.
            player.closeInventory();
            service.requestPremiumPurchase(player);
            return;
        }

        int[] data = slotToLevelAndTrack.get(slot);
        if (data == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int level = data[0];
        String track = data[1] == 1 ? BattlePassService.TRACK_PREMIUM : BattlePassService.TRACK_FREE;
        service.claimReward(player, level, track, this::refresh);
    }
}
