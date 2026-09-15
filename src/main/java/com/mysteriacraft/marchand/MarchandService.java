package com.mysteriacraft.marchand;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.core.storage.Database;
import com.mysteriacraft.customitems.CustomItemManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Orchestre les PNJ Marchands : invocation (villageois immobile/invulnerable marque via
 * PersistentDataContainer avec l'id du marchand, persiste automatiquement avec le monde comme
 * toute entite), rotation quotidienne des offres, limites d'achat par periode, reduction fidelite
 * cumulative, et echange effectif d'une offre contre des pieces d'echange.
 */
public class MarchandService {

    private final Plugin plugin;
    private final Database database;
    private final CustomItemManager customItemManager;
    private final RewardGiver rewardGiver;
    private final MessageManager messages;
    private final NamespacedKey npcIdKey;

    public MarchandService(Plugin plugin, Database database, CustomItemManager customItemManager,
                            RewardGiver rewardGiver, MessageManager messages) {
        this.plugin = plugin;
        this.database = database;
        this.customItemManager = customItemManager;
        this.rewardGiver = rewardGiver;
        this.messages = messages;
        this.npcIdKey = new NamespacedKey(plugin, "npc-marchand-id");
        createTables();
    }

    private void createTables() {
        Connection connection = database.getConnection();
        String achats = "CREATE TABLE IF NOT EXISTS marchand_achats_periode (" +
                "uuid TEXT NOT NULL, marchand_id TEXT NOT NULL, offre_id TEXT NOT NULL, " +
                "periode_cle TEXT NOT NULL, compte INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, marchand_id, offre_id, periode_cle));";
        String fidelite = "CREATE TABLE IF NOT EXISTS marchand_fidelite (" +
                "uuid TEXT NOT NULL, marchand_id TEXT NOT NULL, total INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, marchand_id));";
        try (PreparedStatement s1 = connection.prepareStatement(achats);
             PreparedStatement s2 = connection.prepareStatement(fidelite)) {
            s1.executeUpdate();
            s2.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur creation tables marchand : " + e.getMessage());
        }
    }

    // ---- Invocation ----

    /** Invoque ce PNJ Marchand a cet emplacement : immobile, invulnerable, ne peut ni se
     * reproduire ni se transformer (zombifier), et persiste au redemarrage comme toute entite. */
    public void spawnNpc(Location location, MarchandDefinition definition) {
        Villager villager = location.getWorld().spawn(location, Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setPersistent(true);
            entity.setCanPickupItems(false);
            entity.setCustomName(MessageManager.color(definition.npcName()));
            entity.setCustomNameVisible(true);
            entity.setProfession(Villager.Profession.NONE);
            entity.getPersistentDataContainer().set(npcIdKey, PersistentDataType.STRING, definition.id());
        });
        villager.setRemoveWhenFarAway(false);
    }

    /** Id du marchand marque sur cette entite, ou null si ce n'en est pas une. */
    public String getMarchandId(Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(npcIdKey, PersistentDataType.STRING);
    }

    // ---- Rotation quotidienne des offres (voir "offres-actives-par-jour") ----

    /** Offres EFFECTIVEMENT proposees aujourd'hui par ce marchand : toutes si "offres-actives-par-jour"
     * vaut 0 ou depasse le nombre d'offres, sinon un sous-ensemble tire au sort MAIS IDENTIQUE pour
     * tous les joueurs et stable sur toute une journee (seed = date du jour + id du marchand). */
    public List<MarchandOffer> getActiveOffers(MarchandDefinition definition) {
        int count = definition.offresActivesParJour();
        List<MarchandOffer> all = definition.offres();
        if (count <= 0 || count >= all.size()) {
            return all;
        }
        List<MarchandOffer> shuffled = new ArrayList<>(all);
        long seed = LocalDate.now().toEpochDay() * 1_000_003L + definition.id().hashCode();
        Collections.shuffle(shuffled, new Random(seed));
        return shuffled.subList(0, count);
    }

    // ---- Cle de periode (memes conventions que le module Quetes) ----

    private String currentPeriodKey(MarchandOffer.LimitePeriode periode) {
        LocalDate today = LocalDate.now();
        if (periode == MarchandOffer.LimitePeriode.SEMAINE) {
            WeekFields weekFields = WeekFields.of(Locale.FRANCE);
            return "S-" + today.get(weekFields.weekBasedYear()) + "-" + today.get(weekFields.weekOfWeekBasedYear());
        }
        return "J-" + today;
    }

    /** Nombre d'achats DEJA effectues de cette offre par ce joueur sur la periode courante (0 si
     * l'offre n'est pas limitee). */
    public int countPurchasesThisPeriod(UUID uuid, MarchandDefinition definition, MarchandOffer offer) {
        if (!offer.isLimited()) {
            return 0;
        }
        String periodeCle = currentPeriodKey(offer.limitePeriode());
        String sql = "SELECT compte FROM marchand_achats_periode WHERE uuid = ? AND marchand_id = ? AND offre_id = ? AND periode_cle = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, definition.id());
            statement.setString(3, offer.id());
            statement.setString(4, periodeCle);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("compte");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture achats marchand pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    private void incrementPurchasesThisPeriod(UUID uuid, MarchandDefinition definition, MarchandOffer offer) {
        String periodeCle = currentPeriodKey(offer.limitePeriode());
        String upsert = "INSERT INTO marchand_achats_periode (uuid, marchand_id, offre_id, periode_cle, compte) VALUES (?, ?, ?, ?, 1) " +
                "ON CONFLICT(uuid, marchand_id, offre_id, periode_cle) DO UPDATE SET compte = compte + 1;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, definition.id());
            statement.setString(3, offer.id());
            statement.setString(4, periodeCle);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour achats marchand pour " + uuid + " : " + e.getMessage());
        }
    }

    // ---- Fidelite ----

    public int getFideliteTotal(UUID uuid, MarchandDefinition definition) {
        String sql = "SELECT total FROM marchand_fidelite WHERE uuid = ? AND marchand_id = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, definition.id());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture fidelite marchand pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    private void incrementFidelite(UUID uuid, MarchandDefinition definition) {
        String upsert = "INSERT INTO marchand_fidelite (uuid, marchand_id, total) VALUES (?, ?, 1) " +
                "ON CONFLICT(uuid, marchand_id) DO UPDATE SET total = total + 1;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, definition.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour fidelite marchand pour " + uuid + " : " + e.getMessage());
        }
    }

    /** Reduction fidelite actuelle (%) de ce joueur pour ce marchand, 0 si fidelite desactivee. */
    public double getFideliteDiscountPercent(UUID uuid, MarchandDefinition definition) {
        if (definition.fideliteSeuil() <= 0) {
            return 0;
        }
        int total = getFideliteTotal(uuid, definition);
        int paliers = total / definition.fideliteSeuil();
        return Math.min(definition.fideliteReductionMaxPourcent(), paliers * definition.fideliteReductionPourcent());
    }

    /** Cout EFFECTIF (apres reduction fidelite, arrondi, minimum 1) de cette offre pour ce joueur. */
    public int getEffectiveCost(UUID uuid, MarchandDefinition definition, MarchandOffer offer) {
        double discount = getFideliteDiscountPercent(uuid, definition);
        return Math.max(1, (int) Math.round(offer.cout() * (1 - discount / 100.0)));
    }

    // ---- Monnaie ----

    public int countPieces(Player player, String pieceItemId) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && pieceItemId.equalsIgnoreCase(customItemManager.getCustomItemId(item))) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void removePieces(Player player, String pieceItemId, int amount) {
        int remaining = amount;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || !pieceItemId.equalsIgnoreCase(customItemManager.getCustomItemId(item))) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            if (take >= item.getAmount()) {
                inventory.setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - take);
            }
            remaining -= take;
        }
    }

    // ---- Achat ----

    /** Echange une offre : verifie la limite de periode, calcule le cout effectif (fidelite),
     * verifie/retire les pieces, puis donne la recompense via RewardGiver et met a jour les
     * compteurs d'achats/fidelite. */
    public void purchase(Player player, MarchandDefinition definition, MarchandOffer offer) {
        if (offer.recompense() == null) {
            messages.send(player, "marchand.offre-invalide");
            return;
        }
        if (offer.isLimited() && countPurchasesThisPeriod(player.getUniqueId(), definition, offer) >= offer.limiteQuantite()) {
            messages.send(player, "marchand.limite-atteinte");
            return;
        }

        int effectiveCost = getEffectiveCost(player.getUniqueId(), definition, offer);
        if (countPieces(player, definition.pieceItemId()) < effectiveCost) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("cout", String.valueOf(effectiveCost));
            messages.send(player, "marchand.pieces-insuffisantes", placeholders);
            return;
        }

        removePieces(player, definition.pieceItemId(), effectiveCost);
        if (offer.isLimited()) {
            incrementPurchasesThisPeriod(player.getUniqueId(), definition, offer);
        }
        incrementFidelite(player.getUniqueId(), definition);
        rewardGiver.give(player, offer.recompense());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("recompense", offer.displayName());
        placeholders.put("cout", String.valueOf(effectiveCost));
        messages.send(player, "marchand.echange-reussi", placeholders);
    }
}
