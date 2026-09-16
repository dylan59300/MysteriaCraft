package com.mysteriacraft.marchand;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.core.storage.Database;
import com.mysteriacraft.customitems.CustomItemDefinition;
import com.mysteriacraft.customitems.CustomItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
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
 * toute entite), rotation quotidienne des offres (propre a CHAQUE PNJ physique, voir
 * getActiveOffers), limites d'achat par periode, reduction fidelite cumulative, et interface
 * d'echange EN NATIF (le vrai ecran de troc des villageois vanilla, voir openMerchant) plutot qu'un
 * menu-coffre custom : seuls les items proposes changent (items custom au lieu de ressources
 * vanilla), toute l'experience visuelle/interaction reste celle d'un villageois normal.
 */
public class MarchandService {

    private final Plugin plugin;
    private final Database database;
    private final CustomItemManager customItemManager;
    private final RewardGiver rewardGiver;
    private final MessageManager messages;
    private final NamespacedKey npcIdKey;

    /** Etat d'un ecran de troc natif actuellement ouvert : le joueur, la definition, et les offres/
     * rachats consideres (rotation deja figee) a partir desquels les recettes sont (re)construites
     * a chaque ouverture et apres chaque echange (pour retirer celles qui viennent d'atteindre leur
     * limite de periode et refleter un eventuel changement de palier de fidelite). */
    private static final class MerchantSession {
        final Player player;
        final MarchandDefinition definition;
        final List<MarchandOffer> offers;
        final List<MarchandRachat> rachats;
        List<Object> currentEntries = List.of();

        MerchantSession(Player player, MarchandDefinition definition, List<MarchandOffer> offers, List<MarchandRachat> rachats) {
            this.player = player;
            this.definition = definition;
            this.offers = offers;
            this.rachats = rachats;
        }
    }

    private final Map<Merchant, MerchantSession> openSessions = new HashMap<>();

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
        String rachats = "CREATE TABLE IF NOT EXISTS marchand_rachats_periode (" +
                "uuid TEXT NOT NULL, marchand_id TEXT NOT NULL, rachat_id TEXT NOT NULL, " +
                "periode_cle TEXT NOT NULL, compte INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, marchand_id, rachat_id, periode_cle));";
        try (PreparedStatement s1 = connection.prepareStatement(achats);
             PreparedStatement s2 = connection.prepareStatement(fidelite);
             PreparedStatement s3 = connection.prepareStatement(rachats)) {
            s1.executeUpdate();
            s2.executeUpdate();
            s3.executeUpdate();
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

    /** Offres EFFECTIVEMENT proposees aujourd'hui par CE PNJ precis : toutes si
     * "offres-actives-par-jour" vaut 0 ou depasse le nombre d'offres, sinon un sous-ensemble tire au
     * sort stable sur toute une journee (seed = date du jour + id du marchand + UUID de CETTE
     * entite). Chaque PNJ physique invoque (meme du meme type "marchand") a donc sa PROPRE rotation
     * independante des autres : spawner plusieurs marchands identiques donne des offres variees
     * plutot que strictement les memes partout, contrairement a l'ancien comportement (seed
     * uniquement basee sur le type de marchand, identique pour tous les PNJ du meme type). */
    public List<MarchandOffer> getActiveOffers(MarchandDefinition definition, UUID npcInstanceId) {
        int count = definition.offresActivesParJour();
        List<MarchandOffer> all = definition.offres();
        if (count <= 0 || count >= all.size()) {
            return all;
        }
        List<MarchandOffer> shuffled = new ArrayList<>(all);
        long seed = LocalDate.now().toEpochDay() * 1_000_003L + definition.id().hashCode() * 31L
                + (npcInstanceId != null ? npcInstanceId.hashCode() : 0);
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

    // ---- Rachat (sens inverse : le joueur vend des ressources contre une recompense) ----

    /** Nombre de rachats DEJA effectues de ce rachat par ce joueur sur la periode courante (0 si
     * le rachat n'est pas limite). */
    public int countRachatsThisPeriod(UUID uuid, MarchandDefinition definition, MarchandRachat rachat) {
        if (!rachat.isLimited()) {
            return 0;
        }
        String periodeCle = currentPeriodKey(rachat.limitePeriode());
        String sql = "SELECT compte FROM marchand_rachats_periode WHERE uuid = ? AND marchand_id = ? AND rachat_id = ? AND periode_cle = ?;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, definition.id());
            statement.setString(3, rachat.id());
            statement.setString(4, periodeCle);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("compte");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lecture rachats marchand pour " + uuid + " : " + e.getMessage());
        }
        return 0;
    }

    private void incrementRachatsThisPeriod(UUID uuid, MarchandDefinition definition, MarchandRachat rachat) {
        String periodeCle = currentPeriodKey(rachat.limitePeriode());
        String upsert = "INSERT INTO marchand_rachats_periode (uuid, marchand_id, rachat_id, periode_cle, compte) VALUES (?, ?, ?, ?, 1) " +
                "ON CONFLICT(uuid, marchand_id, rachat_id, periode_cle) DO UPDATE SET compte = compte + 1;";
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, definition.id());
            statement.setString(3, rachat.id());
            statement.setString(4, periodeCle);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur mise a jour rachats marchand pour " + uuid + " : " + e.getMessage());
        }
    }

    /** Vend "quantite" exemplaires de "materiel" contre la recompense du rachat : verifie la
     * limite de periode et la presence des objets dans l'inventaire, puis les retire et donne la
     * recompense via RewardGiver. */
    public void rachat(Player player, MarchandDefinition definition, MarchandRachat rachat) {
        if (rachat.recompense() == null) {
            messages.send(player, "marchand.offre-invalide");
            return;
        }
        if (rachat.isLimited() && countRachatsThisPeriod(player.getUniqueId(), definition, rachat) >= rachat.limiteQuantite()) {
            messages.send(player, "marchand.limite-atteinte");
            return;
        }

        ItemStack required = new ItemStack(rachat.materiel(), rachat.quantite());
        if (!player.getInventory().containsAtLeast(required, rachat.quantite())) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("quantite", String.valueOf(rachat.quantite()));
            placeholders.put("materiel", rachat.materiel().name().replace('_', ' ').toLowerCase());
            messages.send(player, "marchand.objets-insuffisants", placeholders);
            return;
        }

        player.getInventory().removeItem(required);
        if (rachat.isLimited()) {
            incrementRachatsThisPeriod(player.getUniqueId(), definition, rachat);
        }
        rewardGiver.give(player, rachat.recompense());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(rachat.quantite()));
        placeholders.put("materiel", rachat.materiel().name().replace('_', ' ').toLowerCase());
        placeholders.put("recompense", rachat.recompense().displayName());
        messages.send(player, "marchand.rachat-reussi", placeholders);
    }

    // ---- Ecran de troc natif (voir MarchandListener) ----

    /** Ouvre pour ce joueur le VRAI ecran de troc des villageois (Merchant natif de Bukkit) pour ce
     * PNJ : offres (payees en pieces d'echange) ET rachats (payes en materiel vendu) apparaissent
     * comme des trocs normaux dans la MEME liste, exactement comme un villageois vanilla propose
     * plusieurs trocs a la fois. npcInstanceId (l'UUID de l'entite cliquee) fixe la rotation
     * quotidienne des offres PROPRE a ce PNJ (voir getActiveOffers). */
    public void openMerchant(Player player, MarchandDefinition definition, UUID npcInstanceId) {
        List<MarchandOffer> offers = getActiveOffers(definition, npcInstanceId);
        List<MarchandRachat> rachats = definition.rachats();

        String title = MessageManager.color(messages.raw("marchand.titre-gui").replace("{nom}", definition.npcName()));
        Merchant merchant = Bukkit.createMerchant(title);
        MerchantSession session = new MerchantSession(player, definition, offers, rachats);
        openSessions.put(merchant, session);
        refreshRecipes(merchant, session);
        player.openMerchant(merchant, true);
    }

    /** (Re)construit la liste des recettes de troc pour ce joueur : ignore silencieusement toute
     * offre/rachat sans recompense valide ou ayant deja atteint sa limite de periode pour lui (au
     * lieu de l'afficher grisee, elle disparait simplement de la liste jusqu'au prochain
     * renouvellement de periode). "currentEntries" garde, dans le MEME ordre que les recettes
     * envoyees au client, l'offre ou le rachat correspondant a chaque index (voir handleTrade). */
    private void refreshRecipes(Merchant merchant, MerchantSession session) {
        UUID uuid = session.player.getUniqueId();
        List<MerchantRecipe> recipes = new ArrayList<>();
        List<Object> entries = new ArrayList<>();

        for (MarchandOffer offer : session.offers) {
            if (offer.recompense() == null) {
                continue;
            }
            if (offer.isLimited() && countPurchasesThisPeriod(uuid, session.definition, offer) >= offer.limiteQuantite()) {
                continue;
            }
            int cost = getEffectiveCost(uuid, session.definition, offer);
            ItemStack ingredient = createPieceStack(session.definition.pieceItemId(), cost);

            MerchantRecipe recipe = new MerchantRecipe(offer.icon(), Integer.MAX_VALUE);
            recipe.setExperienceReward(false);
            recipe.setIngredients(List.of(ingredient));
            recipes.add(recipe);
            entries.add(offer);
        }

        for (MarchandRachat rachat : session.rachats) {
            if (rachat.recompense() == null) {
                continue;
            }
            if (rachat.isLimited() && countRachatsThisPeriod(uuid, session.definition, rachat) >= rachat.limiteQuantite()) {
                continue;
            }
            ItemStack ingredient = new ItemStack(rachat.materiel(), rachat.quantite());

            MerchantRecipe recipe = new MerchantRecipe(rachat.recompense().displayIcon(), Integer.MAX_VALUE);
            recipe.setExperienceReward(false);
            recipe.setIngredients(List.of(ingredient));
            recipes.add(recipe);
            entries.add(rachat);
        }

        session.currentEntries = entries;
        merchant.setRecipes(recipes);
    }

    private ItemStack createPieceStack(String pieceItemId, int amount) {
        CustomItemDefinition pieceDefinition = customItemManager.getItem(pieceItemId);
        return pieceDefinition != null
                ? customItemManager.createItem(pieceDefinition, amount)
                : new ItemStack(Material.EMERALD, amount);
    }

    /** true si ce Merchant est un ecran de troc de PNJ Marchand ouvert par ce plugin (par
     * opposition a un vrai villageois vanilla, que MarchandListener ne doit pas toucher). */
    public boolean isTrackedMerchant(Merchant merchant) {
        return openSessions.containsKey(merchant);
    }

    /** Traite le clic sur le resultat d'un troc (voir MarchandListener) : retrouve l'offre ou le
     * rachat correspondant a l'index de la recette selectionnee et delegue a purchase()/rachat(),
     * puis reconstruit les recettes (cout/fidelite/limites a jour). Ne fait rien si le Merchant
     * n'est pas suivi ou si l'index ne correspond a rien (ecran deja perime, ex: apres reload). */
    public void handleTrade(Player player, Merchant merchant, int index) {
        MerchantSession session = openSessions.get(merchant);
        if (session == null || index < 0 || index >= session.currentEntries.size()) {
            return;
        }
        Object entry = session.currentEntries.get(index);
        if (entry instanceof MarchandOffer offer) {
            purchase(player, session.definition, offer);
        } else if (entry instanceof MarchandRachat rachat) {
            rachat(player, session.definition, rachat);
        }
        refreshRecipes(merchant, session);
    }

    /** A appeler a la fermeture de l'ecran de troc (voir MarchandListener) pour ne pas garder une
     * reference indefiniment (le Merchant n'est autrement rattache a aucune entite persistante). */
    public void forgetSession(Merchant merchant) {
        openSessions.remove(merchant);
    }
}
