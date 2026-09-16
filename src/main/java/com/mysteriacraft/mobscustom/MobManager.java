package com.mysteriacraft.mobscustom;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mobs custom (voir mobs_custom.yml) : un mob vanilla (type configurable) avec un nom, une vie et
 * des degats personnalises, et une loot table DEDIEE (reutilise le systeme generique de Reward)
 * distribuee au tueur a sa mort. Le mob est marque via son PersistentDataContainer (les entites,
 * contrairement aux blocs, en ont un propre nativement) : aucun suivi SQLite necessaire.
 */
public class MobManager {

    public record MobDefinition(String id, String nom, EntityType type, double vieMax, double degats, List<Reward> loot) {
    }

    private final Plugin plugin;
    private final ConfigManager mobsConfig;
    private final NamespacedKey mobKey;
    private final Map<String, MobDefinition> mobs = new LinkedHashMap<>();

    public MobManager(Plugin plugin, ConfigManager mobsConfig) {
        this.plugin = plugin;
        this.mobsConfig = mobsConfig;
        this.mobKey = new NamespacedKey(plugin, "mob-custom-id");
        load();
    }

    public void load() {
        mobs.clear();
        ConfigurationSection root = mobsConfig.get().getConfigurationSection("mobs");
        if (root == null) {
            plugin.getLogger().warning("Aucun mob custom trouve (section 'mobs' manquante).");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            EntityType type;
            try {
                type = EntityType.valueOf(section.getString("type", "ZOMBIE").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Type de mob custom invalide pour '" + id + "'.");
                continue;
            }
            String nom = section.getString("nom", id);
            double vieMax = section.getDouble("vie-max", 20);
            double degats = section.getDouble("degats", 3);

            List<Reward> loot = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("loot")) {
                @SuppressWarnings("unchecked")
                ConfigurationSection lootSection = mapToSection((Map<String, Object>) raw);
                Reward reward = RewardParser.parse(lootSection);
                if (reward != null) {
                    loot.add(reward);
                }
            }
            mobs.put(id.toLowerCase(), new MobDefinition(id.toLowerCase(), nom, type, vieMax, degats, loot));
        }
        plugin.getLogger().info(mobs.size() + " mob(s) custom charge(s).");
    }

    private ConfigurationSection mapToSection(Map<String, Object> map) {
        org.bukkit.configuration.MemoryConfiguration memoryConfig = new org.bukkit.configuration.MemoryConfiguration();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            memoryConfig.set(entry.getKey(), entry.getValue());
        }
        return memoryConfig;
    }

    public MobDefinition getMob(String id) {
        return id == null ? null : mobs.get(id.toLowerCase());
    }

    public java.util.Set<String> getMobIds() {
        return mobs.keySet();
    }

    /** Fait apparaitre ce mob custom a cet endroit et renvoie l'entite creee (null si le type ne
     * produit pas de LivingEntity). */
    public LivingEntity spawn(Location location, MobDefinition definition) {
        Entity entity = location.getWorld().spawnEntity(location, definition.type());
        if (!(entity instanceof LivingEntity livingEntity)) {
            entity.remove();
            return null;
        }
        livingEntity.setCustomName(MessageManager.color(definition.nom()));
        livingEntity.setCustomNameVisible(true);

        AttributeInstance maxHealth = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(definition.vieMax());
            livingEntity.setHealth(definition.vieMax());
        }
        AttributeInstance degats = livingEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (degats != null) {
            degats.setBaseValue(definition.degats());
        }

        livingEntity.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, definition.id());
        return livingEntity;
    }

    /** id de mob custom marque sur cette entite, ou null si ce n'en est pas une. */
    public String getMobId(Entity entity) {
        return entity.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
    }
}
