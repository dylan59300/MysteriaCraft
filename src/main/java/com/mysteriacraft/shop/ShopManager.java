package com.mysteriacraft.shop;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import com.mysteriacraft.core.reward.RewardType;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * (0 = non rachetable). categoryId reste celui de sa VRAIE categorie meme lorsque l'article
     * est affiche dans la categorie virtuelle "Favoris" (voir getFavorites), pour que
     * basculer un favori depuis cet ecran cible la bonne cle. */
    public record ShopItem(String id, String categoryId, String displayName, ItemStack icon, Reward reward,
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
    private final Database database;
    private final ConfigManager shopConfig;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    public ShopManager(Plugin plugin, Database database, ConfigManager shopConfig) {
        this.plugin = plugin;
        this.database = database;
        this.shopConfig = shopConfig;
        createFavoritesTable();
        loadCategories();
    }

    private void createFavoritesTable() {
        String sql = "CREATE TABLE IF NOT EXISTS shop_favoris (" +
                "uuid TEXT NOT NULL, " +
                "cle TEXT NOT NULL, " +
                "PRIMARY KEY (uuid, cle)" +
                ");";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'shop_favoris' : " + e.getMessage());
        }
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
                items.add(new ShopItem(itemId.toLowerCase(), categoryId.toLowerCase(), reward.displayName(),
                        reward.displayIcon(), reward, buyPrice, sellPrice));
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

    // ---- Favoris (voir ShopItemsGui : shift-clic pour basculer) ----

    private static String favoriteKey(String categoryId, String itemId) {
        return categoryId.toLowerCase() + ":" + itemId.toLowerCase();
    }

    /** Requete synchrone : a appeler hors du thread principal. */
    public boolean isFavorite(UUID uuid, String categoryId, String itemId) {
        String select = "SELECT 1 FROM shop_favoris WHERE uuid = ? AND cle = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, favoriteKey(categoryId, itemId));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture favori pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    /** Requete synchrone : a appeler hors du thread principal. Renvoie le nouvel etat (true = ajoute). */
    public boolean toggleFavorite(UUID uuid, String categoryId, String itemId) {
        String key = favoriteKey(categoryId, itemId);
        Connection connection = database.getConnection();
        if (isFavorite(uuid, categoryId, itemId)) {
            String delete = "DELETE FROM shop_favoris WHERE uuid = ? AND cle = ?;";
            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, key);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur suppression favori pour " + uuid + " : " + e.getMessage());
            }
            return false;
        }
        String insert = "INSERT OR IGNORE INTO shop_favoris (uuid, cle) VALUES (?, ?);";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur ajout favori pour " + uuid + " : " + e.getMessage());
        }
        return true;
    }

    /** Tous les articles favoris de ce joueur, toutes categories confondues. Requete synchrone :
     * a appeler hors du thread principal. */
    public List<ShopItem> getFavorites(UUID uuid) {
        List<ShopItem> favorites = new ArrayList<>();
        String select = "SELECT cle FROM shop_favoris WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String[] parts = rs.getString("cle").split(":", 2);
                    if (parts.length != 2) {
                        continue;
                    }
                    ShopCategory category = getCategory(parts[0]);
                    ShopItem item = getItem(category, parts[1]);
                    if (item != null) {
                        favorites.add(item);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture favoris pour " + uuid + " : " + e.getMessage());
        }
        return favorites;
    }
}
