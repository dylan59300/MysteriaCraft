package com.mysteriacraft.marchand;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Charge la configuration du PNJ Marchand (id de la monnaie d'echange, nom affiche, offres)
 * depuis marchand.yml.
 */
public class MarchandManager {

    private final Plugin plugin;
    private final ConfigManager marchandConfig;

    private String pieceItemId = "piece_echange";
    private String npcName = "&6&lMarchand";
    private final List<MarchandOffer> offers = new ArrayList<>();

    public MarchandManager(Plugin plugin, ConfigManager marchandConfig) {
        this.plugin = plugin;
        this.marchandConfig = marchandConfig;
        loadConfig();
    }

    public void loadConfig() {
        offers.clear();
        pieceItemId = marchandConfig.get().getString("id-piece", "piece_echange");
        npcName = marchandConfig.get().getString("nom-pnj", "&6&lMarchand");

        ConfigurationSection root = marchandConfig.get().getConfigurationSection("offres");
        if (root == null) {
            plugin.getLogger().warning("Aucune offre trouvee dans marchand.yml (section 'offres' manquante).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                offers.add(parseOffer(id, section));
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement offre marchand '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(offers.size() + " offre(s) du PNJ Marchand chargee(s) depuis marchand.yml.");
    }

    private MarchandOffer parseOffer(String id, ConfigurationSection section) {
        int cout = Math.max(1, section.getInt("cout", 1));
        Reward recompense = RewardParser.parse(section.getConfigurationSection("recompense"));

        String displayName = recompense != null ? recompense.displayName() : id;
        ItemStack icon = recompense != null ? recompense.displayIcon() : new ItemBuilder(Material.PAPER).name(id).build();

        return new MarchandOffer(id, cout, recompense, displayName, icon);
    }

    public List<MarchandOffer> getOffers() {
        return offers;
    }

    public MarchandOffer getOffer(String id) {
        for (MarchandOffer offer : offers) {
            if (offer.id().equalsIgnoreCase(id)) {
                return offer;
            }
        }
        return null;
    }

    public String getPieceItemId() {
        return pieceItemId;
    }

    public String getNpcName() {
        return npcName;
    }
}
