package com.mysteriacraft.shop.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.shop.PromotionManager;
import com.mysteriacraft.shop.ShopManager;
import com.mysteriacraft.shop.ShopService;
import com.mysteriacraft.shop.StockManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menu listant les articles d'une categorie de boutique (ou de la categorie virtuelle "Favoris").
 * Clic gauche = acheter, clic droit = revendre (si l'article tenu en main correspond, voir
 * ShopService#sell), shift-clic (gauche ou droit) = basculer le favori de cet article.
 * Affiche le prix barre + le prix final des lors qu'une reduction (voir PromotionManager)
 * s'applique a cet article pour ce joueur.
 */
public class ShopItemsGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int BACK_SLOT = 18;
    private static final int PREVIOUS_SLOT = 19;
    private static final int NEXT_SLOT = 25;

    private final Plugin plugin;
    private final ShopManager.ShopCategory category;
    private final ShopManager manager;
    private final ShopService service;
    private final EconomyManager economyManager;
    private final PromotionManager promotionManager;
    private final StockManager stockManager;
    private final MessageManager messages;
    private final Map<Integer, String> slotToItemId = new HashMap<>();

    private Inventory inventory;
    private int page = 0;

    public ShopItemsGui(Plugin plugin, Player viewer, ShopManager.ShopCategory category, ShopManager manager,
                         ShopService service, EconomyManager economyManager, PromotionManager promotionManager,
                         StockManager stockManager, MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
        this.category = category;
        this.manager = manager;
        this.service = service;
        this.economyManager = economyManager;
        this.promotionManager = promotionManager;
        this.stockManager = stockManager;
        this.messages = messages;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(category.displayName()));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(category.items().size() / (double) ITEM_SLOTS.length));
    }

    private void render() {
        slotToItemId.clear();

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        List<ShopManager.ShopItem> items = category.items();
        int firstIndex = page * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= items.size()) {
                break;
            }
            ShopManager.ShopItem item = items.get(index);
            inventory.setItem(ITEM_SLOTS[i], buildItemIcon(viewer.getUniqueId(), item));
            slotToItemId.put(ITEM_SLOTS[i], item.id());
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW).name(messages.raw("boutique.gui-retour")).build());

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

    private ItemStack buildItemIcon(UUID viewerId, ShopManager.ShopItem item) {
        ItemStack icon = item.icon() != null ? item.icon().clone() : new ItemStack(Material.STONE);
        boolean favorite = manager.isFavorite(viewerId, item.categoryId(), item.id());

        List<String> lore = new ArrayList<>();
        List<String> badges = new ArrayList<>();
        if (item.isPurchasable()) {
            List<String> toutesLesCles = manager.getAllItemKeysSorted();
            double reduction = promotionManager.getReductionAutomatiquePourcent(viewer, item.categoryId(), item.id(), toutesLesCles);
            if (reduction > 0) {
                double prixFinal = item.buyPrice() * (1.0 - reduction / 100.0);
                lore.add("&7Prix : &m" + economyManager.format(item.buyPrice()));
                lore.add(replace(messages.raw("boutique.gui-prix-reduit"), "prix", economyManager.format(prixFinal)));

                if (promotionManager.isHappyHourActive()) {
                    badges.add(messages.raw("boutique.gui-badge-happy-hour"));
                }
                if (promotionManager.estArticleDuJour(item.categoryId(), item.id(), toutesLesCles)) {
                    badges.add(messages.raw("boutique.gui-badge-article-du-jour"));
                }
                if (!promotionManager.aDejaAchete(viewerId)) {
                    badges.add(messages.raw("boutique.gui-badge-premier-achat"));
                }
                if (promotionManager.estAnniversaireAujourdhui(viewer)) {
                    badges.add(messages.raw("boutique.gui-badge-anniversaire"));
                }
                if (promotionManager.getReductionVipPourcent(viewer) > 0) {
                    badges.add(messages.raw("boutique.gui-badge-vip"));
                }
            } else {
                lore.add(replace(messages.raw("boutique.gui-prix-achat"), "prix", economyManager.format(item.buyPrice())));
            }
        } else {
            lore.add(messages.raw("boutique.gui-non-achetable"));
        }
        if (item.isPurchasable() && item.hasStockLimite()) {
            int stockRestant = stockManager.getStockRestant(item);
            if (stockRestant > 0) {
                lore.add(replace(messages.raw("boutique.gui-stock-restant"), "stock", String.valueOf(stockRestant)));
            } else {
                long minutes = stockManager.getMinutesAvantProchainReappro(item);
                lore.add(replace(messages.raw("boutique.gui-stock-epuise"), "minutes", String.valueOf(minutes)));
            }
        }
        if (item.isSellable()) {
            lore.add(replace(messages.raw("boutique.gui-prix-vente"), "prix", economyManager.format(item.sellPrice())));
        }
        lore.addAll(badges);
        lore.add("");
        if (item.isPurchasable()) {
            lore.add(messages.raw("boutique.gui-clic-acheter"));
        }
        if (item.isSellable()) {
            lore.add(messages.raw("boutique.gui-clic-vendre"));
        }
        lore.add(favorite ? messages.raw("boutique.gui-favori-retirer") : messages.raw("boutique.gui-favori-ajouter"));
        if (favorite) {
            lore.add(0, messages.raw("boutique.gui-favori-marque"));
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> combined = new ArrayList<>(meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of());
            combined.add("");
            for (String line : lore) {
                combined.add(MessageManager.color(line));
            }
            meta.setLore(combined);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == BACK_SLOT) {
            if (event.getWhoClicked() instanceof Player player) {
                new ShopCategoriesGui(plugin, player, manager, service, economyManager, promotionManager, stockManager, messages).open();
            }
            return;
        }
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

        String itemId = slotToItemId.get(slot);
        if (itemId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ShopManager.ShopItem item = manager.getItem(category, itemId);
        if (item == null) {
            return;
        }

        if (event.isShiftClick()) {
            toggleFavorite(player, item);
            return;
        }
        if (event.getClick() == ClickType.RIGHT) {
            service.sell(player, item);
        } else {
            service.buy(player, item);
        }
        render();
    }

    private void toggleFavorite(Player player, ShopManager.ShopItem item) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            manager.toggleFavorite(player.getUniqueId(), item.categoryId(), item.id());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    render();
                }
            });
        });
    }
}
