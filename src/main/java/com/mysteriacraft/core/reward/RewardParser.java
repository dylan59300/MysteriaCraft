package com.mysteriacraft.core.reward;

import com.mysteriacraft.core.gui.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * Parse une Reward depuis une section YAML unique (utilise par BattlePass et Quetes, qui
 * decrivent chacun une seule recompense par section, contrairement a Crates dont la table de
 * loot est une liste de maps geree separement dans CrateManager).
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
            case "CLE_CAISSE" -> parseCrateKey(section);
            case "BOOST_XP" -> parseXpBoost(section);
            case "PET" -> parsePet(section);
            default -> parseItem(section);
        };
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

    private static Reward parseCrateKey(ConfigurationSection section) {
        String crateId = section.getString("caisse", "");
        int amount = Math.max(1, section.getInt("quantite", 1));
        String displayName = section.getString("nom", amount + " cle(s) - " + crateId);
        Material iconMaterial = Material.matchMaterial(section.getString("icone", "TRIPWIRE_HOOK"));
        if (iconMaterial == null) {
            iconMaterial = Material.TRIPWIRE_HOOK;
        }
        ItemStack icon = new ItemBuilder(iconMaterial, amount).name("&e" + displayName).build();
        return Reward.ofCrateKey(crateId, amount, displayName, icon);
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
