package fr.vanadia.mobs;

import fr.vanadia.Vanadia;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

public class MobManager {

    private final Vanadia plugin;
    private final Map<String, CustomMob> customMobs;
    private final Random random;

    public static final String CUSTOM_MOB_KEY = "vanadia_custom_mob";

    public MobManager(Vanadia plugin) {
        this.plugin = plugin;
        this.customMobs = new HashMap<>();
        this.random = new Random();
        loadMobs();
    }

    private void loadMobs() {
        customMobs.put("zombie-sorcier", new CustomMob(
                "zombie-sorcier",
                "&5Zombie Sorcier",
                EntityType.ZOMBIE,
                40, 8, 25, 0.1,
                List.of("ROTTEN_FLESH:3", "GOLD_INGOT:1", "ENDER_PEARL:1")
        ));

        customMobs.put("squelette-maudit", new CustomMob(
                "squelette-maudit",
                "&4Squelette Maudit",
                EntityType.SKELETON,
                35, 10, 30, 0.08,
                List.of("BONE:5", "ARROW:10", "BOW:1")
        ));

        customMobs.put("araignee-venimeuse", new CustomMob(
                "araignee-venimeuse",
                "&2Araignee Venimeuse",
                EntityType.SPIDER,
                30, 6, 20, 0.12,
                List.of("STRING:4", "SPIDER_EYE:2", "FERMENTED_SPIDER_EYE:1")
        ));

        customMobs.put("golem-ancien", new CustomMob(
                "golem-ancien",
                "&6Golem Ancien",
                EntityType.IRON_GOLEM,
                200, 20, 100, 0.02,
                List.of("IRON_BLOCK:2", "DIAMOND:3", "GOLDEN_APPLE:1")
        ));
    }

    public void reload() {
        customMobs.clear();
        loadMobs();
    }

    public CustomMob getCustomMob(String id) {
        return customMobs.get(id);
    }

    public Collection<CustomMob> getAllCustomMobs() {
        return customMobs.values();
    }

    public boolean shouldSpawnCustom(EntityType entityType) {
        for (CustomMob mob : customMobs.values()) {
            if (mob.getBaseEntity() == entityType) {
                return random.nextDouble() < mob.getSpawnChance();
            }
        }
        return false;
    }

    public CustomMob getRandomMobForEntity(EntityType entityType) {
        List<CustomMob> matching = new ArrayList<>();
        for (CustomMob mob : customMobs.values()) {
            if (mob.getBaseEntity() == entityType) {
                matching.add(mob);
            }
        }
        if (matching.isEmpty()) return null;
        return matching.get(random.nextInt(matching.size()));
    }

    public void applyCustomMob(LivingEntity entity, CustomMob customMob) {
        entity.customName(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(customMob.getDisplayName()));
        entity.setCustomNameVisible(true);

        entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(customMob.getHealth());
        entity.setHealth(customMob.getHealth());

        if (entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(customMob.getDamage());
        }

        entity.setMetadata(CUSTOM_MOB_KEY, new FixedMetadataValue(plugin, customMob.getId()));
    }

    public boolean isCustomMob(LivingEntity entity) {
        return entity.hasMetadata(CUSTOM_MOB_KEY);
    }

    public CustomMob getCustomMobFromEntity(LivingEntity entity) {
        if (!entity.hasMetadata(CUSTOM_MOB_KEY)) return null;
        String id = entity.getMetadata(CUSTOM_MOB_KEY).get(0).asString();
        return customMobs.get(id);
    }
}
