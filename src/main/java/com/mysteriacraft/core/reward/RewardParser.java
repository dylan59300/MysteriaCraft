package com.mysteriacraft.core.reward;

import com.mysteriacraft.core.gui.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * Parse une Reward depuis une section YAML unique (utilise par BattlePass et Quetes, qui
 * decrivent chacun une seule recompense par section).
 */
public final class RewardParser {

    private RewardParser() {
    }

    public static Reward parse(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String type = section.getString("type", "ITEM").toUpperCase();

        return switch (type) {
            case "ECONOMIE" -> parseEconomie(section);
            case "BOOST_XP" -> parseXpBoost(section);
            case "PET" -> parsePet(section);
            case "LUCKYBLOCK" -> parseLuckyBlock(section);
            case "OBJET_CUSTOM" -> parseCustomItem(section);
            case "GENERATEUR" -> parseGenerator(section);
            case "MACHINE" -> parseMachine(section);
            case "AGRANDISSEMENT_ILE" -> parseIslandUpgrade(section);
            case "TITRE_CHAT" -> parseTitle(section);
            default -> parseItem(section);
        };
    }

    private static Reward parseTitle(ConfigurationSection section) {
        String titre = section.getString("titre", "");
        String displayName = section.getString("nom", "Titre : " + titre);
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "NAME_TAG"));
        if (iconMaterial == null) {
            iconMaterial = Material.NAME_TAG;
        }
        ItemStack icon = new ItemBuilder(iconMaterial).name("&e" + displayName).build();
        return Reward.ofTitle(titre, displayName, icon);
    }

    private static Reward parseIslandUpgrade(ConfigurationSection section) {
        int blocks = Math.max(1, section.getInt("blocs", 5));
        String displayName = section.getString("nom", "+" + blocks + " blocs sur son ile");
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "GRASS_BLOCK"));
        if (iconMaterial == null) {
            iconMaterial = Material.GRASS_BLOCK;
        }
        ItemStack icon = new ItemBuilder(iconMaterial).name("&e" + displayName).build();
        return Reward.ofIslandUpgrade(blocks, displayName, icon);
    }

    private static Reward parseMachine(ConfigurationSection section) {
        String machineId = section.getString("machine-id", "transformation");
        int amount = Math.max(1, section.getInt("quantite", 1));
        String displayName = section.getString("nom", "Machine : " + machineId);
        Material iconMaterial = Material.matchMaterial(section.getString("icone",
                machineId.equalsIgnoreCase("miniere") ? "NETHERITE_BLOCK" : "IRON_BLOCK"));
        if (iconMaterial == null) {
            iconMaterial = Material.IRON_BLOCK;
        }
        ItemStack icon = new ItemBuilder(iconMaterial, amount).name("&e" + displayName).build();
        return Reward.ofMachine(machineId, amount, displayName, icon);
    }

    private static Reward parseGenerator(ConfigurationSection section) {
        String generatorTypeId = section.getString("type-id", "");
        int amount = Math.max(1, section.getInt("quantite", 1));
        String displayName = section.getString("nom", "Generateur : " + generatorTypeId);
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "IRON_ORE"));
        if (iconMaterial == null) {
            iconMaterial = Material.IRON_ORE;
        }
        ItemStack icon = new ItemBuilder(iconMaterial, amount).name("&e" + displayName).build();
        return Reward.ofGenerator(generatorTypeId, amount, displayName, icon);
    }

    private static Reward parseCustomItem(ConfigurationSection section) {
        String customItemId = section.getString("item-id", "");
        int amount = Math.max(1, section.getInt("quantite", 1));
        String displayName = section.getString("nom", "Objet custom : " + customItemId);
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "IRON_INGOT"));
        if (iconMaterial == null) {
            iconMaterial = Material.IRON_INGOT;
        }
        ItemStack icon = new ItemBuilder(iconMaterial, amount).name("&e" + displayName).build();
        return Reward.ofCustomItem(customItemId, amount, displayName, icon);
    }

    private static Reward parseLuckyBlock(ConfigurationSection section) {
        String familyId = section.getString("famille", "");
        String displayName = section.getString("nom", "Lucky Block : " + familyId);
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "GOLD_BLOCK"));
        if (iconMaterial == null) {
            iconMaterial = Material.GOLD_BLOCK;
        }
        ItemStack icon = new ItemBuilder(iconMaterial).name("&e" + displayName).build();
        return Reward.ofLuckyBlock(familyId, displayName, icon);
    }

    private static Reward parsePet(ConfigurationSection section) {
        String petId = section.getString("pet-id", "");
        String displayName = section.getString("nom", "Pet : " + petId);
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "BONE"));
        if (iconMaterial == null) {
            iconMaterial = Material.BONE;
        }
        ItemStack icon = new ItemBuilder(iconMaterial).name("&e" + displayName).build();
        return Reward.ofPet(petId, displayName, icon);
    }

    private static Reward parseEconomie(ConfigurationSection section) {
        double amount = section.getDouble("montant", 0);
        String displayName = section.getString("nom", (long) amount + "$");
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "GOLD_INGOT"));
        if (iconMaterial == null) {
            iconMaterial = Material.GOLD_INGOT;
        }
        ItemStack icon = new ItemBuilder(iconMaterial).name("&e" + displayName).build();
        return Reward.ofEconomy(amount, displayName, icon);
    }

    private static Reward parseXpBoost(ConfigurationSection section) {
        long durationSeconds = section.getLong("duree-secondes", 600);
        double multiplier = section.getDouble("multiplicateur", 2.0);
        String displayName = section.getString("nom", "&dBoost XP x" + multiplier);
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "NETHER_STAR"));
        if (iconMaterial == null) {
            iconMaterial = Material.NETHER_STAR;
        }
        ItemStack icon = new ItemBuilder(iconMaterial).name(displayName).build();
        return Reward.ofXpBooster(durationSeconds, multiplier, displayName, icon);
    }

    private static Reward parseItem(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("materiel", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        int quantity = Math.max(1, section.getInt("quantite", 1));
        ItemStack item = new ItemStack(material, quantity);
        String displayName = section.getString("nom", material.name().replace('_', ' ') + " x" + quantity);
        ItemStack icon = new ItemBuilder(material, quantity).name("&f" + displayName).build();
        return Reward.ofItem(item, displayName, icon);
    }
}
