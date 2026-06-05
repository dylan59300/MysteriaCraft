package fr.vanadia.items;

import fr.vanadia.classes.ClassType;
import org.bukkit.Material;

public class CustomItem {

    private final String id;
    private final String name;
    private final String lore;
    private final Material material;
    private final ClassType requiredClass;
    private final int requiredLevel;
    private final double bonusDamage;
    private final double bonusDefense;
    private final double bonusHealth;

    public CustomItem(String id, String name, String lore, Material material,
                      ClassType requiredClass, int requiredLevel,
                      double bonusDamage, double bonusDefense, double bonusHealth) {
        this.id = id;
        this.name = name;
        this.lore = lore;
        this.material = material;
        this.requiredClass = requiredClass;
        this.requiredLevel = requiredLevel;
        this.bonusDamage = bonusDamage;
        this.bonusDefense = bonusDefense;
        this.bonusHealth = bonusHealth;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLore() {
        return lore;
    }

    public Material getMaterial() {
        return material;
    }

    public ClassType getRequiredClass() {
        return requiredClass;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public double getBonusDamage() {
        return bonusDamage;
    }

    public double getBonusDefense() {
        return bonusDefense;
    }

    public double getBonusHealth() {
        return bonusHealth;
    }
}
