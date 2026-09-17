package com.mysteriacraft.gemmes;

import com.mysteriacraft.core.config.ConfigManager;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Charge les types de gemmes depuis gemmes.yml : chacune est liee a un item custom consommable
 * (voir custom_items.yml) et accorde un bonus permanent sur un attribut vanilla (degats, armure,
 * vitesse, vie max) une fois inseree dans un equipement (voir GemmeService#inserer).
 */
public class GemmeManager {

    public record GemmeType(String id, String nom, Attribute attribut, double valeur) {
    }

    private final Plugin plugin;
    private final ConfigManager gemmesConfig;
    private final NamespacedKey gemmeKey;
    private final Map<String, GemmeType> types = new LinkedHashMap<>();
    /** id d'item custom -> id de type de gemme (une gemme = un item custom dedie). */
    private final Map<String, String> itemIdToType = new LinkedHashMap<>();

    public GemmeManager(Plugin plugin, ConfigManager gemmesConfig) {
        this.plugin = plugin;
        this.gemmesConfig = gemmesConfig;
        this.gemmeKey = new NamespacedKey(plugin, "gemme-inseree");
        load();
    }

    public void load() {
        types.clear();
        itemIdToType.clear();
        ConfigurationSection root = gemmesConfig.get().getConfigurationSection("gemmes");
        if (root == null) {
            plugin.getLogger().warning("Aucune gemme trouvee (section 'gemmes' manquante).");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String nom = section.getString("nom", id);
            Attribute attribut;
            try {
                attribut = Attribute.valueOf(section.getString("attribut", "ATTACK_DAMAGE").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Attribut de gemme invalide pour '" + id + "' : " + section.getString("attribut"));
                continue;
            }
            double valeur = section.getDouble("valeur", 1.0);
            String itemId = section.getString("item-id", id);
            types.put(id.toLowerCase(), new GemmeType(id.toLowerCase(), nom, attribut, valeur));
            itemIdToType.put(itemId.toLowerCase(), id.toLowerCase());
        }
        plugin.getLogger().info(types.size() + " type(s) de gemme charge(s).");
    }

    public GemmeType getTypeFromItemId(String customItemId) {
        if (customItemId == null) {
            return null;
        }
        String typeId = itemIdToType.get(customItemId.toLowerCase());
        return typeId == null ? null : types.get(typeId);
    }

    public GemmeType getType(String id) {
        return id == null ? null : types.get(id.toLowerCase());
    }

    public NamespacedKey getGemmeKey() {
        return gemmeKey;
    }
}
