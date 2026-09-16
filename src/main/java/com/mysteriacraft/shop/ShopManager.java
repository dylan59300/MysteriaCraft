package com.mysteriacraft.shop;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import com.mysteriacraft.core.reward.RewardType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Charge les categories/articles de la Boutique depuis boutique.yml. Chaque article reutilise le
 * systeme generique de Reward (voir com.mysteriacraft.core.reward) pour decrire ce qu'il donne a
 * l'achat : n'importe quel type de recompense (item, economie, objet custom, generateur...) peut
 * donc etre vendu en boutique sans code supplementaire. La revente ("prix-vente") n'est possible
 * que pour les articles de type ITEM ou OBJET_CUSTOM (les seuls qui correspondent a un ItemStack
 * concret que le joueur peut tenir en main pour le revendre).
 */
public class ShopManager {

    /** Un article de boutique : ce qu'il donne (reward), son prix d'achat et son prix de revente
     * (0 = non rachetable). */
    public record ShopItem(String id, String displayName, ItemStack icon, Reward reward,
                            double buyPrice, double sellPrice) {

        public boolean isPurchasable() {
            return buyPrice > 0;
        }

        public boolean isSellable() {
            return sellPrice > 0 && (reward.type() == RewardType.ITEM || reward.type() == RewardType.OBJET_CUSTOM);
        }
    }

    /** Une categorie de boutique regroupant plusieurs articles. */
    public record ShopCategory(String id, String displayName, Material icon, List<ShopItem> items) {
    }

    private final Plugin plugin;
    private final ConfigManager shopConfig;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    public ShopManager(Plugin plugin, ConfigManager shopConfig) {
        this.plugin = plugin;
        this.shopConfig = shopConfig;
        loadCategories();
    }

    public void loadCategories() {
        categories.clear();
        ConfigurationSection root = shopConfig.get().getConfigurationSection("categories");
        if (root == null) {
            plugin.getLogger().warning("Aucune categorie de boutique trouvee (section 'categories' manquante).");
            return;
        }

        for (String categoryId : root.getKeys(false)) {
            ConfigurationSection categorySection = root.getConfigurationSection(categoryId);
            if (categorySection == null) {
                continue;
            }
            try {
                categories.put(categoryId.toLowerCase(), parseCategory(categoryId, categorySection));
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement categorie de boutique '" + categoryId + "' : " + e.getMessage());
            }
        }
        int totalItems = categories.values().stream().mapToInt(c -> c.items().size()).sum();
        plugin.getLogger().info(categories.size() + " categorie(s) de boutique chargee(s) (" + totalItems + " article(s)).");
    }

    private ShopCategory parseCategory(String categoryId, ConfigurationSection section) {
        String displayName = section.getString("nom", categoryId);
        Material icon = Material.matchMaterial(section.getString("icone", "CHEST"));
        if (icon == null) {
            icon = Material.CHEST;
        }

        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemId : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                if (itemSection == null) {
                    continue;
                }
                Reward reward = RewardParser.parse(itemSection.getConfigurationSection("recompense"));
                if (reward == null) {
                    plugin.getLogger().warning("Article de boutique '" + itemId + "' (categorie '" + categoryId
                            + "') ignore : recompense invalide/manquante.");
                    continue;
                }
                double buyPrice = itemSection.getDouble("prix-achat", 0);
                double sellPrice = itemSection.getDouble("prix-vente", 0);
                items.add(new ShopItem(itemId.toLowerCase(), reward.displayName(), reward.displayIcon(),
                        reward, buyPrice, sellPrice));
            }
        }
        return new ShopCategory(categoryId.toLowerCase(), displayName, icon, items);
    }

    public Collection<ShopCategory> getCategoriesSorted() {
        return categories.values();
    }

    public ShopCategory getCategory(String id) {
        return id == null ? null : categories.get(id.toLowerCase());
    }

    public ShopItem getItem(ShopCategory category, String itemId) {
        if (category == null || itemId == null) {
            return null;
        }
        for (ShopItem item : category.items()) {
            if (item.id().equalsIgnoreCase(itemId)) {
                return item;
            }
        }
        return null;
    }
}
