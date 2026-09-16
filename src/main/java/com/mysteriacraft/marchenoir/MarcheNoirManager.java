package com.mysteriacraft.marchenoir;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Charge le pool d'objets rares du Marche Noir depuis marche_noir.yml. La selection ACTIVE (N
 * objets parmi le pool, chacun a un prix aleatoire entre prix-min et prix-max) se renouvelle a
 * echeance reguliere (rotation-heures) : le tirage est deterministe (seed = jour + creneau de
 * rotation), donc identique pour tous les joueurs jusqu'a la prochaine rotation, sans tache
 * planifiee necessaire (calcule a la demande).
 */
public class MarcheNoirManager {

    public record PoolEntry(String id, Reward reward, double prixMin, double prixMax) {
    }

    public record OffreActive(String id, Reward reward, double prix) {
    }

    private final Plugin plugin;
    private final ConfigManager marcheNoirConfig;
    private final Map<String, PoolEntry> pool = new LinkedHashMap<>();

    public MarcheNoirManager(Plugin plugin, ConfigManager marcheNoirConfig) {
        this.plugin = plugin;
        this.marcheNoirConfig = marcheNoirConfig;
        load();
    }

    public void load() {
        pool.clear();
        ConfigurationSection root = marcheNoirConfig.get().getConfigurationSection("objets");
        if (root == null) {
            plugin.getLogger().warning("Aucun objet de Marche Noir trouve (section 'objets' manquante).");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Reward reward = RewardParser.parse(section.getConfigurationSection("recompense"));
            if (reward == null) {
                plugin.getLogger().warning("Objet de Marche Noir '" + id + "' ignore : recompense invalide/manquante.");
                continue;
            }
            double prixMin = section.getDouble("prix-min", 100);
            double prixMax = Math.max(prixMin, section.getDouble("prix-max", prixMin * 2));
            pool.put(id.toLowerCase(), new PoolEntry(id.toLowerCase(), reward, prixMin, prixMax));
        }
        plugin.getLogger().info(pool.size() + " objet(s) dans le pool du Marche Noir.");
    }

    private int getRotationHeures() {
        return Math.max(1, marcheNoirConfig.get().getInt("rotation-heures", 6));
    }

    private int getNombreObjetsActifs() {
        return Math.max(1, marcheNoirConfig.get().getInt("nombre-objets-actifs", 3));
    }

    /** Numero du creneau de rotation actuel (change toutes les rotation-heures, identique pour
     * tout le serveur a un instant donne). */
    private long getCreneauActuel() {
        long epochHeures = System.currentTimeMillis() / (1000L * 60 * 60);
        return epochHeures / getRotationHeures();
    }

    /** Millisecondes restantes avant la prochaine rotation. */
    public long getMillisAvantRotation() {
        long rotationMillis = getRotationHeures() * 3600_000L;
        long creneauDebutMillis = getCreneauActuel() * rotationMillis;
        long now = System.currentTimeMillis();
        return rotationMillis - (now - creneauDebutMillis);
    }

    /** Selection actuelle (deterministe pour ce creneau), avec un prix aleatoire (mais fixe pour
     * ce creneau) entre prix-min et prix-max de chaque objet tire. */
    public List<OffreActive> getOffresActuelles() {
        List<PoolEntry> entries = new ArrayList<>(pool.values());
        if (entries.isEmpty()) {
            return List.of();
        }
        long seed = getCreneauActuel() * 1_000_003L + LocalDate.now().toEpochDay();
        Random random = new Random(seed);
        Collections.shuffle(entries, random);

        int count = Math.min(getNombreObjetsActifs(), entries.size());
        List<OffreActive> offres = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PoolEntry entry = entries.get(i);
            double prix = entry.prixMin() + random.nextDouble() * (entry.prixMax() - entry.prixMin());
            offres.add(new OffreActive(entry.id(), entry.reward(), Math.round(prix)));
        }
        return offres;
    }

    public OffreActive getOffre(String id) {
        return getOffresActuelles().stream().filter(offre -> offre.id().equalsIgnoreCase(id)).findFirst().orElse(null);
    }
}
