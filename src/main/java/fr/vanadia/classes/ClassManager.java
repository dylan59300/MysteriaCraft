package fr.vanadia.classes;

import fr.vanadia.Vanadia;
import fr.vanadia.player.PlayerData;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class ClassManager {

    private final Vanadia plugin;

    public ClassManager(Vanadia plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // Reload class configs from config.yml if needed
    }

    public boolean setClass(Player player, ClassType classType) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) return false;

        data.setClassType(classType);
        applyClassStats(player, data);
        return true;
    }

    public void applyClassStats(Player player, PlayerData data) {
        ClassType classType = data.getClassType();
        if (classType == null) return;

        int level = data.getLevel();
        double healthBonus = level * 0.5;
        double maxHealth = classType.getBaseHealth() + healthBonus;

        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        player.setHealth(Math.min(player.getHealth(), maxHealth));
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(classType.getBaseSpeed());
    }

    public double calculateDamage(PlayerData data) {
        if (data.getClassType() == null) return 1.0;
        return data.getClassType().getBaseDamage() + (data.getLevel() * 0.3);
    }

    public double calculateDefense(PlayerData data) {
        if (data.getClassType() == null) return 0.0;
        return data.getClassType().getBaseDefense() + (data.getLevel() * 0.2);
    }
}
