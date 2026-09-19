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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panier de la Boutique (voir /boutique panier) : les articles ajoutes depuis ShopItemsGui
 * (touche "laisser tomber"/Q) apparaissent ici avec leur quantite et un total indicatif ; "Valider"
 * achete tout d'un coup (voir ShopService#validerPanier), cliquer sur une ligne la retire.
 */
public class ShopCartGui extends Menu {

    private static final int SIZE = 36;
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26
    };
    private static final int VALIDER_SLOT = 31;
    private static final int VIDER_SLOT = 29;
    private static final int RETOUR_SLOT = 33;

    private final Plugin plugin;
    private final ShopManager manager;
    private final ShopService service;
    private final EconomyManager economyManager;
    private final PromotionManager promotionManager;
    private final StockManager stockManager;
    private final MessageManager messages;

    private Inventory inventory;
    private final Map<Integer, Integer> slotToIndex = new HashMap<>();

    public ShopCartGui(Plugin plugin, Player viewer, ShopManager manager, ShopService service,
                        EconomyManager economyManager, PromotionManager promotionManager,
                        StockManager stockManager, MessageManager messages) {
        super(viewer);
        this.plugin = plugin;
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
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("boutique.panier-titre")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        inventory.clear();
        slotToIndex.clear();

        List<ShopService.CartEntry> panier = service.getPanier(viewer.getUniqueId());
        for (int i = 0; i < CONTENT_SLOTS.length && i < panier.size(); i++) {
            ShopService.CartEntry entree = panier.get(i);
            double reduction = promotionManager.getReductionAutomatiquePourcent(viewer, entree.item().categoryId(),
                    entree.item().id(), manager.getAllItemKeysSorted());
            double prixUnitaire = entree.item().buyPrice() * (1.0 - reduction / 100.0);
            double sousTotal = prixUnitaire * entree.quantite();

            ItemStack icon = entree.item().icon() != null ? entree.item().icon().clone() : new ItemStack(Material.STONE);
            List<String> lore = List.of(
                    replace(messages.raw("boutique.panier-ligne-quantite"), "quantite", String.valueOf(entree.quantite())),
                    replace(messages.raw("boutique.panier-ligne-total"), "total", economyManager.format(sousTotal)),
                    messages.raw("boutique.panier-ligne-retirer"));
            inventory.setItem(CONTENT_SLOTS[i], new ItemBuilder(icon.getType())
                    .name(entree.item().displayName())
                    .lore(lore)
                    .build());
            slotToIndex.put(CONTENT_SLOTS[i], i);
        }

        double total = service.calculerTotalPanier(viewer);
        inventory.setItem(VALIDER_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(messages.raw("boutique.panier-valider-nom"))
                .lore(List.of(replace(messages.raw("boutique.panier-valider-lore"), "total", economyManager.format(total))))
                .build());
        inventory.setItem(VIDER_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.raw("boutique.panier-vider-nom"))
                .build());
        inventory.setItem(RETOUR_SLOT, new ItemBuilder(Material.ARROW)
                .name(messages.raw("boutique.gui-retour"))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (slot == RETOUR_SLOT) {
            new ShopCategoriesGui(plugin, player, manager, service, economyManager, promotionManager, stockManager, messages).open();
            return;
        }
        if (slot == VIDER_SLOT) {
            service.viderPanier(player.getUniqueId());
            render();
            return;
        }
        if (slot == VALIDER_SLOT) {
            service.validerPanier(player);
            render();
            return;
        }
        Integer index = slotToIndex.get(slot);
        if (index != null) {
            service.retirerDuPanier(player.getUniqueId(), index);
            render();
        }
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }
}
