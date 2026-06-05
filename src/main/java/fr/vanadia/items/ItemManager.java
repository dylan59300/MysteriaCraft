package fr.vanadia.items;

import fr.vanadia.Vanadia;
import fr.vanadia.classes.ClassType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ItemManager {

    private final Vanadia plugin;
    private final Map<String, CustomItem> customItems;
    private final NamespacedKey customItemKey;

    public ItemManager(Vanadia plugin) {
        this.plugin = plugin;
        this.customItems = new HashMap<>();
        this.customItemKey = new NamespacedKey(plugin, "custom_item");
        loadItems();
    }

    private void loadItems() {
        customItems.put("epee-guerrier", new CustomItem(
                "epee-guerrier", "&cEpee du Guerrier",
                "Une epee forgee pour les plus braves",
                Material.DIAMOND_SWORD, ClassType.GUERRIER, 5,
                5.0, 0, 0
        ));

        customItems.put("baton-mage", new CustomItem(
                "baton-mage", "&9Baton Arcanique",
                "Un baton impregne de magie ancienne",
                Material.BLAZE_ROD, ClassType.MAGE, 5,
                8.0, 0, 0
        ));

        customItems.put("arc-archer", new CustomItem(
                "arc-archer", "&aArc de Precision",
                "Un arc fabrique par les meilleurs artisans",
                Material.BOW, ClassType.ARCHER, 5,
                6.0, 0, 0
        ));

        customItems.put("dague-assassin", new CustomItem(
                "dague-assassin", "&5Dague de l'Ombre",
                "Une dague empoisonnee mortelle",
                Material.IRON_SWORD, ClassType.ASSASSIN, 5,
                7.0, 0, 0
        ));

        customItems.put("bouclier-ancien", new CustomItem(
                "bouclier-ancien", "&6Bouclier Ancien",
                "Un bouclier indestructible des temps anciens",
                Material.SHIELD, ClassType.GUERRIER, 10,
                0, 8.0, 10.0
        ));

        customItems.put("amulette-vie", new CustomItem(
                "amulette-vie", "&dAmulette de Vie",
                "Augmente la vitalite de son porteur",
                Material.EMERALD, null, 8,
                0, 2.0, 20.0
        ));
    }

    public CustomItem getCustomItem(String id) {
        return customItems.get(id);
    }

    public Collection<CustomItem> getAllItems() {
        return customItems.values();
    }

    public ItemStack createItemStack(String itemId) {
        CustomItem customItem = customItems.get(itemId);
        if (customItem == null) return null;

        ItemStack item = new ItemStack(customItem.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(customItem.getName().replace("&c", "").replace("&9", "")
                        .replace("&a", "").replace("&5", "").replace("&6", "").replace("&d", ""))
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(customItem.getLore()).color(NamedTextColor.GRAY));
        lore.add(Component.empty());

        if (customItem.getBonusDamage() > 0) {
            lore.add(Component.text("+" + (int) customItem.getBonusDamage() + " Degats")
                    .color(NamedTextColor.RED));
        }
        if (customItem.getBonusDefense() > 0) {
            lore.add(Component.text("+" + (int) customItem.getBonusDefense() + " Defense")
                    .color(NamedTextColor.BLUE));
        }
        if (customItem.getBonusHealth() > 0) {
            lore.add(Component.text("+" + (int) customItem.getBonusHealth() + " PV")
                    .color(NamedTextColor.GREEN));
        }

        if (customItem.getRequiredClass() != null) {
            lore.add(Component.empty());
            lore.add(Component.text("Classe requise: " + customItem.getRequiredClass().getDisplayName())
                    .color(NamedTextColor.YELLOW));
        }
        lore.add(Component.text("Niveau requis: " + customItem.getRequiredLevel())
                .color(NamedTextColor.YELLOW));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(customItemKey, PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);

        return item;
    }

    public String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(customItemKey, PersistentDataType.STRING);
    }
}
