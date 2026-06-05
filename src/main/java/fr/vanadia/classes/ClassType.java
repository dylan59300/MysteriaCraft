package fr.vanadia.classes;

public enum ClassType {
    GUERRIER("Guerrier", "&c", 30, 8, 10, 0.2, 0),
    MAGE("Mage", "&9", 20, 12, 4, 0.2, 100),
    ARCHER("Archer", "&a", 22, 10, 6, 0.25, 0),
    ASSASSIN("Assassin", "&5", 18, 14, 3, 0.3, 0);

    private final String displayName;
    private final String color;
    private final double baseHealth;
    private final double baseDamage;
    private final double baseDefense;
    private final double baseSpeed;
    private final int baseMana;

    ClassType(String displayName, String color, double baseHealth, double baseDamage,
              double baseDefense, double baseSpeed, int baseMana) {
        this.displayName = displayName;
        this.color = color;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.baseDefense = baseDefense;
        this.baseSpeed = baseSpeed;
        this.baseMana = baseMana;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColoredName() {
        return color + displayName;
    }

    public String getColor() {
        return color;
    }

    public double getBaseHealth() {
        return baseHealth;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public double getBaseDefense() {
        return baseDefense;
    }

    public double getBaseSpeed() {
        return baseSpeed;
    }

    public int getBaseMana() {
        return baseMana;
    }

    public static ClassType fromString(String name) {
        for (ClassType type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
