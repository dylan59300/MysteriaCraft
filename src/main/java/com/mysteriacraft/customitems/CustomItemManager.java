package com.mysteriacraft.customitems;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Charge les minerais/objets custom depuis custom_items.yml et fabrique leurs ItemStack
 * (item de base vanilla + nom/lore custom + marque PersistentDataContainer). Aucun resource
 * pack requis : chaque item custom est visuellement un item vanilla existant (choisi via
 * "item-de-base"), distingue par son nom colore et sa lore.
 */
public class CustomItemManager {

    private final Plugin plugin;
    private final ConfigManager customItemsConfig;
    private final NamespacedKey itemKey;
    private final NamespacedKey durabilityKey;

    private final Map<String, CustomItemDefinition> items = new LinkedHashMap<>();
    /** Index inverse pour retrouver rapidement, a la casse d'un bloc, quels items custom peuvent en tomber. */
    private final Map<Material, List<CustomItemDefinition>> itemsBySourceOre = new HashMap<>();

    public CustomItemManager(Plugin plugin, ConfigManager customItemsConfig) {
        this.plugin = plugin;
        this.customItemsConfig = customItemsConfig;
        this.itemKey = new NamespacedKey(plugin, "custom-item");
        this.durabilityKey = new NamespacedKey(plugin, "custom-item-durabilite");
        loadItems();
    }

    public void loadItems() {
        items.clear();
        itemsBySourceOre.clear();

        ConfigurationSection root = customItemsConfig.get().getConfigurationSection("items");
        if (root == null) {
            plugin.getLogger().warning("Aucun item custom trouve (section 'items' manquante).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                CustomItemDefinition definition = parseItem(id, section);
                items.put(id.toLowerCase(), definition);
                for (Material ore : definition.sourceOres()) {
                    itemsBySourceOre.computeIfAbsent(ore, k -> new ArrayList<>()).add(definition);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement item custom '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(items.size() + " item(s) custom charge(s) depuis custom_items.yml.");
        registerRecipes();
    }

    /** (Re)enregistre les recettes d'etabli des items custom qui en declarent une (voir "recette" en config).
     * Retire d'abord l'ancienne recette de chaque item (meme ceux qui n'en ont plus), pour qu'un rechargement
     * de config qui supprime une recette la desenregistre bien du jeu. */
    private void registerRecipes() {
        for (CustomItemDefinition definition : items.values()) {
            NamespacedKey key = new NamespacedKey(plugin, "customitem-" + definition.id());
            Bukkit.removeRecipe(key);
            if (!definition.isCraftable()) {
                continue;
            }

            // shape()/setIngredient() peuvent eux aussi lancer IllegalArgumentException (forme
            // invalide, caractere absent de la forme...) : toute la construction de la recette est
            // donc protegee, pas seulement addRecipe(), pour qu'une recette mal configuree dans
            // custom_items.yml n'empeche jamais le plugin entier de demarrer.
            try {
                ShapedRecipe recipe = new ShapedRecipe(key, createItem(definition));
                recipe.shape(definition.recipeShape().toArray(new String[0]));
                for (Map.Entry<Character, Material> entry : definition.recipeIngredients().entrySet()) {
                    recipe.setIngredient(entry.getKey(), entry.getValue());
                }
                Bukkit.addRecipe(recipe);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("Recette invalide pour l'item custom '" + definition.id() + "' : " + e.getMessage());
            }
        }
    }

    private CustomItemDefinition parseItem(String id, ConfigurationSection section) {
        String displayName = section.getString("nom", id);
        List<String> lore = section.getStringList("lore");
        Material baseItem = Material.matchMaterial(section.getString("item-de-base", "IRON_INGOT"));
        if (baseItem == null) {
            baseItem = Material.IRON_INGOT;
        }
        double dropChance = section.getDouble("chance-drop", 0);
        double sellPrice = section.getDouble("prix-vente", 0);

        List<Material> sourceOres = new ArrayList<>();
        for (String materialName : section.getStringList("minerais-source")) {
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                sourceOres.add(material);
            } else {
                plugin.getLogger().warning("Materiau source inconnu pour l'item custom '" + id + "' : " + materialName);
            }
        }

        List<String> recipeShape = List.of();
        Map<Character, Material> recipeIngredients = new LinkedHashMap<>();
        ConfigurationSection recette = section.getConfigurationSection("recette");
        if (recette != null) {
            recipeShape = recette.getStringList("forme");
            ConfigurationSection ingredientsSection = recette.getConfigurationSection("ingredients");
            if (ingredientsSection != null) {
                for (String charKey : ingredientsSection.getKeys(false)) {
                    if (charKey.length() != 1) {
                        plugin.getLogger().warning("Cle d'ingredient invalide (1 caractere attendu) pour l'item custom '"
                                + id + "' : " + charKey);
                        continue;
                    }
                    Material ingredientMaterial = Material.matchMaterial(ingredientsSection.getString(charKey));
                    if (ingredientMaterial == null) {
                        plugin.getLogger().warning("Materiau d'ingredient inconnu pour l'item custom '" + id + "' : "
                                + ingredientsSection.getString(charKey));
                        continue;
                    }
                    recipeIngredients.put(charKey.charAt(0), ingredientMaterial);
                }
            }
        }

        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        ConfigurationSection enchantSection = section.getConfigurationSection("enchantements");
        if (enchantSection != null) {
            for (String enchantKey : enchantSection.getKeys(false)) {
                Enchantment enchantment = Enchantment.getByName(enchantKey.toUpperCase());
                if (enchantment == null) {
                    plugin.getLogger().warning("Enchantement inconnu pour l'item custom '" + id + "' : " + enchantKey);
                    continue;
                }
                enchantments.put(enchantment, Math.max(1, enchantSection.getInt(enchantKey, 1)));
            }
        }
        double extraAttackDamage = section.getDouble("degats-bonus", 0);
        double extraArmor = section.getDouble("armure-bonus", 0);
        boolean unbreakable = section.getBoolean("incassable", false);
        double volDeVie = section.getDouble("vol-de-vie", 0);
        int durabiliteCustom = Math.max(0, section.getInt("durabilite-custom", 0));
        boolean excluLootMachine = section.getBoolean("exclu-loot-machine", false);

        return new CustomItemDefinition(id, displayName, lore, baseItem, sourceOres, dropChance,
                sellPrice, recipeShape, recipeIngredients, enchantments, extraAttackDamage, extraArmor, unbreakable,
                volDeVie, durabiliteCustom, excluLootMachine);
    }

    public List<CustomItemDefinition> getItemsSorted() {
        return new ArrayList<>(items.values());
    }

    public CustomItemDefinition getItem(String id) {
        return id == null ? null : items.get(id.toLowerCase());
    }

    /** Items custom pouvant tomber de ce type de bloc (liste vide si aucun). */
    public List<CustomItemDefinition> getItemsForOre(Material oreMaterial) {
        return itemsBySourceOre.getOrDefault(oreMaterial, List.of());
    }

    public ItemStack createItem(CustomItemDefinition definition) {
        return createItem(definition, 1);
    }

    public ItemStack createItem(CustomItemDefinition definition, int amount) {
        ItemStack item = new ItemStack(definition.baseItem(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.color(definition.displayName()));
            List<String> coloredLore = new ArrayList<>();
            for (String line : definition.lore()) {
                coloredLore.add(MessageManager.color(line));
            }
            if (definition.hasCustomDurability()) {
                coloredLore.add(MessageManager.color("&7Durabilite : &e" + definition.durabiliteCustom()
                        + "&7/&e" + definition.durabiliteCustom()));
            }
            meta.setLore(coloredLore);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, definition.id());
            if (definition.hasCustomDurability()) {
                meta.getPersistentDataContainer().set(durabilityKey, PersistentDataType.INTEGER, definition.durabiliteCustom());
            }

            // Enchantements "sans limite" (leur niveau vanilla max est ignore) : indispensable pour
            // un equipement "full custom" nettement au-dessus de son equivalent vanilla.
            for (Map.Entry<Enchantment, Integer> entry : definition.enchantments().entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }

            EquipmentSlot slot = resolveEquipmentSlot(definition.baseItem());
            if (definition.extraAttackDamage() > 0 && slot == EquipmentSlot.HAND) {
                meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(
                        UUID.nameUUIDFromBytes(("mysteriacraft-degats-" + definition.id()).getBytes()),
                        "mysteriacraft-degats-bonus", definition.extraAttackDamage(),
                        AttributeModifier.Operation.ADD_NUMBER, slot));
            }
            if (definition.extraArmor() > 0 && slot != null && slot != EquipmentSlot.HAND) {
                meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(
                        UUID.nameUUIDFromBytes(("mysteriacraft-armure-" + definition.id()).getBytes()),
                        "mysteriacraft-armure-bonus", definition.extraArmor(),
                        AttributeModifier.Operation.ADD_NUMBER, slot));
            }
            if (definition.unbreakable()) {
                meta.setUnbreakable(true);
            }
            if (definition.isGear()) {
                // La lore custom decrit deja les bonus : masque les lignes vanilla redondantes
                // (enchantements/attributs/incassable) pour un rendu propre et "full custom".
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    /** Emplacement d'equipement vanilla d'un materiau (arme/outil -> main, piece d'armure -> sa
     * case), ou null si ce n'est ni une arme/outil ni une armure (pour l'attribut a appliquer). */
    private EquipmentSlot resolveEquipmentSlot(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            return EquipmentSlot.HAND;
        }
        return null;
    }

    /** Renvoie l'id de l'item custom marque sur cet ItemStack, ou null si ce n'en est pas un. */
    public String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    // ---- Durabilite custom (voir CustomItemDefinition#durabiliteCustom) ----

    /** Utilisations restantes stockees sur cet ItemStack, ou -1 s'il n'a pas de durabilite custom. */
    public int getRemainingDurability(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return -1;
        }
        Integer remaining = item.getItemMeta().getPersistentDataContainer().get(durabilityKey, PersistentDataType.INTEGER);
        return remaining == null ? -1 : remaining;
    }

    /** Met a jour, EN PLACE sur cet ItemStack, les utilisations restantes et la ligne de lore
     * "Durabilite : X/Y" correspondante (toujours la DERNIERE ligne de lore, ajoutee par
     * createItem() pour tout item avec durabilite custom active). */
    public void setRemainingDurability(ItemStack item, CustomItemDefinition definition, int remaining) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !definition.hasCustomDurability()) {
            return;
        }
        meta.getPersistentDataContainer().set(durabilityKey, PersistentDataType.INTEGER, remaining);
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String durabilityLine = MessageManager.color("&7Durabilite : &e" + remaining + "&7/&e" + definition.durabiliteCustom());
        if (!lore.isEmpty()) {
            lore.set(lore.size() - 1, durabilityLine);
        } else {
            lore.add(durabilityLine);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }
}
