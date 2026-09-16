package com.mysteriacraft.etabli;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardParser;
import com.mysteriacraft.core.storage.Database;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Etabli Ameliore : bloc unique (repere par materiau, sans suivi de position) ouvrant un menu de
 * recettes SECRETES (voir etabli.yml). Chaque recette n'est visible/craftable qu'apres avoir ete
 * "decouverte" en consommant son Parchemin dedie (item custom) sur le bloc - la meme mecanique
 * couvre a la fois "recettes secretes a decouvrir" et "plans de craft a collectionner" : chaque
 * Parchemin trouve (loot/LuckyBlock/Marchand...) EST un plan de craft a collectionner. Le
 * deblocage est permanent et persiste en SQLite.
 */
public class EtabliManager {

    public record Ingredient(Material materiel, String customItemId, int quantite) {
    }

    public record Recette(String id, String nom, Reward resultat, List<Ingredient> ingredients, String parcheminItemId) {
    }

    private final Plugin plugin;
    private final Database database;
    private final ConfigManager etabliConfig;
    private Material blockMaterial = Material.CARTOGRAPHY_TABLE;
    private final Map<String, Recette> recettes = new LinkedHashMap<>();
    private final Map<String, String> parcheminToRecette = new LinkedHashMap<>();

    public EtabliManager(Plugin plugin, Database database, ConfigManager etabliConfig) {
        this.plugin = plugin;
        this.database = database;
        this.etabliConfig = etabliConfig;
        createTable();
        load();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS etabli_debloquees (uuid TEXT NOT NULL, recette_id TEXT NOT NULL, " +
                "PRIMARY KEY (uuid, recette_id));";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation table 'etabli_debloquees' : " + e.getMessage());
        }
    }

    public void load() {
        recettes.clear();
        parcheminToRecette.clear();

        Material material = Material.matchMaterial(etabliConfig.get().getString("bloc", "CARTOGRAPHY_TABLE"));
        blockMaterial = material != null ? material : Material.CARTOGRAPHY_TABLE;

        ConfigurationSection root = etabliConfig.get().getConfigurationSection("recettes");
        if (root == null) {
            plugin.getLogger().warning("Aucune recette d'etabli trouvee (section 'recettes' manquante).");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String nom = section.getString("nom", id);
            Reward resultat = RewardParser.parse(section.getConfigurationSection("resultat"));
            if (resultat == null) {
                plugin.getLogger().warning("Recette d'etabli '" + id + "' ignoree : resultat invalide/manquant.");
                continue;
            }
            List<Ingredient> ingredients = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("ingredients")) {
                Object materielRaw = raw.get("materiel");
                Object itemIdRaw = raw.get("item-id");
                int quantite = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
                if (itemIdRaw != null) {
                    ingredients.add(new Ingredient(null, String.valueOf(itemIdRaw), Math.max(1, quantite)));
                } else if (materielRaw != null) {
                    Material materiel = Material.matchMaterial(String.valueOf(materielRaw));
                    if (materiel != null) {
                        ingredients.add(new Ingredient(materiel, null, Math.max(1, quantite)));
                    }
                }
            }
            String parcheminItemId = section.getString("parchemin-item-id", id + "_parchemin");
            Recette recette = new Recette(id.toLowerCase(), nom, resultat, ingredients, parcheminItemId.toLowerCase());
            recettes.put(recette.id(), recette);
            parcheminToRecette.put(recette.parcheminItemId(), recette.id());
        }
        plugin.getLogger().info(recettes.size() + " recette(s) d'etabli chargee(s).");
    }

    public boolean isEtabliBlock(Material material) {
        return material == blockMaterial;
    }

    public List<Recette> getRecettesSorted() {
        return new ArrayList<>(recettes.values());
    }

    public Recette getRecette(String id) {
        return id == null ? null : recettes.get(id.toLowerCase());
    }

    public Recette getRecetteByParchemin(String parcheminItemId) {
        if (parcheminItemId == null) {
            return null;
        }
        String recetteId = parcheminToRecette.get(parcheminItemId.toLowerCase());
        return recetteId == null ? null : recettes.get(recetteId);
    }

    /** Requete synchrone : a appeler hors du thread principal. */
    public boolean isUnlocked(UUID uuid, String recetteId) {
        String select = "SELECT 1 FROM etabli_debloquees WHERE uuid = ? AND recette_id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, recetteId.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture recette debloquee pour " + uuid + " : " + e.getMessage());
            return false;
        }
    }

    public List<String> getUnlockedRecetteIds(UUID uuid) {
        List<String> ids = new ArrayList<>();
        String select = "SELECT recette_id FROM etabli_debloquees WHERE uuid = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("recette_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture recettes debloquees pour " + uuid + " : " + e.getMessage());
        }
        return ids;
    }

    public void unlock(UUID uuid, String recetteId) {
        String insert = "INSERT OR IGNORE INTO etabli_debloquees (uuid, recette_id) VALUES (?, ?);";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, recetteId.toLowerCase());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur deblocage recette '" + recetteId + "' pour " + uuid + " : " + e.getMessage());
        }
    }
}
