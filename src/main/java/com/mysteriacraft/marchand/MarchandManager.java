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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Charge la configuration de TOUS les PNJ Marchands (voir MarchandDefinition) et du drop passif
 * de pieces d'echange, depuis marchand.yml.
 */
public class MarchandManager {

    private final Plugin plugin;
    private final ConfigManager marchandConfig;

    private final Map<String, MarchandDefinition> marchands = new LinkedHashMap<>();

    private String dropPassifItemId;
    private double dropPassifChanceMinage;
    private double dropPassifChanceMob;
    private int dropPassifQuantite;

    public MarchandManager(Plugin plugin, ConfigManager marchandConfig) {
        this.plugin = plugin;
        this.marchandConfig = marchandConfig;
        loadConfig();
    }

    public void loadConfig() {
        marchands.clear();

        ConfigurationSection dropSection = marchandConfig.get().getConfigurationSection("drop-passif");
        if (dropSection != null) {
            dropPassifItemId = dropSection.getString("item-id", "piece_echange");
            dropPassifChanceMinage = dropSection.getDouble("chance-minage", 0);
            dropPassifChanceMob = dropSection.getDouble("chance-mob", 0);
            dropPassifQuantite = Math.max(1, dropSection.getInt("quantite", 1));
        } else {
            dropPassifItemId = "piece_echange";
            dropPassifChanceMinage = 0;
            dropPassifChanceMob = 0;
            dropPassifQuantite = 1;
        }

        ConfigurationSection root = marchandConfig.get().getConfigurationSection("marchands");
        if (root == null) {
            plugin.getLogger().warning("Aucun PNJ Marchand trouve dans marchand.yml (section 'marchands' manquante).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                marchands.put(id.toLowerCase(), parseMarchand(id, section));
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement PNJ Marchand '" + id + "' : " + e.getMessage());
            }
        }
        plugin.getLogger().info(marchands.size() + " PNJ Marchand(s) charge(s) depuis marchand.yml.");
    }

    private MarchandDefinition parseMarchand(String id, ConfigurationSection section) {
        String oeufItemId = section.getString("oeuf-id", "oeuf_pnj_marchand");
        String pieceItemId = section.getString("id-piece", "piece_echange");
        String npcName = section.getString("nom-pnj", "&6&lMarchand");
        int offresActivesParJour = Math.max(0, section.getInt("offres-actives-par-jour", 0));

        ConfigurationSection fideliteSection = section.getConfigurationSection("fidelite");
        int fideliteSeuil = fideliteSection != null ? Math.max(0, fideliteSection.getInt("seuil", 0)) : 0;
        double fideliteReductionPourcent = fideliteSection != null ? fideliteSection.getDouble("reduction-pourcent", 0) : 0;
        double fideliteReductionMaxPourcent = fideliteSection != null ? fideliteSection.getDouble("reduction-max", 0) : 0;

        List<MarchandOffer> offres = new ArrayList<>();
        ConfigurationSection offresSection = section.getConfigurationSection("offres");
        if (offresSection != null) {
            for (String offerId : offresSection.getKeys(false)) {
                ConfigurationSection offerSection = offresSection.getConfigurationSection(offerId);
                if (offerSection == null) {
                    continue;
                }
                try {
                    offres.add(parseOffer(offerId, offerSection));
                } catch (Exception e) {
                    plugin.getLogger().severe("Erreur chargement offre '" + offerId + "' du marchand '" + id + "' : " + e.getMessage());
                }
            }
        }

        return new MarchandDefinition(id.toLowerCase(), oeufItemId, pieceItemId, npcName, offresActivesParJour,
                fideliteSeuil, fideliteReductionPourcent, fideliteReductionMaxPourcent, offres);
    }

    private MarchandOffer parseOffer(String id, ConfigurationSection section) {
        int cout = Math.max(1, section.getInt("cout", 1));
        MarchandOffer.LimitePeriode limitePeriode = MarchandOffer.LimitePeriode.fromString(
                section.contains("limite-periode") ? section.getString("limite-periode") : null);
        int limiteQuantite = Math.max(0, section.getInt("limite-quantite", 0));
        Reward recompense = RewardParser.parse(section.getConfigurationSection("recompense"));

        String displayName = recompense != null ? recompense.displayName() : id;
        ItemStack icon = recompense != null ? recompense.displayIcon() : new ItemBuilder(Material.PAPER).name(id).build();

        return new MarchandOffer(id, cout, limitePeriode, limiteQuantite, recompense, displayName, icon);
    }

    public List<MarchandDefinition> getMarchands() {
        return new ArrayList<>(marchands.values());
    }

    public MarchandDefinition getMarchand(String id) {
        return id == null ? null : marchands.get(id.toLowerCase());
    }

    /** Retrouve le marchand invoque par cet item custom (voir "oeuf-id"), ou null. */
    public MarchandDefinition getMarchandByOeufId(String oeufItemId) {
        if (oeufItemId == null) {
            return null;
        }
        for (MarchandDefinition definition : marchands.values()) {
            if (definition.oeufItemId().equalsIgnoreCase(oeufItemId)) {
                return definition;
            }
        }
        return null;
    }

    public String getDropPassifItemId() {
        return dropPassifItemId;
    }

    public double getDropPassifChanceMinage() {
        return dropPassifChanceMinage;
    }

    public double getDropPassifChanceMob() {
        return dropPassifChanceMob;
    }

    public int getDropPassifQuantite() {
        return dropPassifQuantite;
    }
}
