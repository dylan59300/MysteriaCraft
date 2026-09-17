package com.mysteriacraft.shop;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.core.reward.RewardType;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.rank.RankManager;
import com.mysteriacraft.talents.TalentManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traite les achats/reventes de la Boutique : l'achat delegue simplement au RewardGiver partage
 * (n'importe quel type de recompense fonctionne), la revente ne s'applique qu'aux articles
 * ITEM/OBJET_CUSTOM et se base sur l'item REELLEMENT tenu en main par le joueur. L'achat applique
 * en plus les reductions automatiques et le code promo actif (voir PromotionManager), et accorde
 * un cashback en Jetons Boutique (voir TokenManager).
 */
public class ShopService {

    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final RewardGiver rewardGiver;
    private final CustomItemManager customItemManager;
    private final RankManager rankManager;
    private final TalentManager talentManager;
    private final PromotionManager promotionManager;
    private final TokenManager tokenManager;
    private final LoyaltyManager loyaltyManager;
    private final MessageManager messages;

    public ShopService(ShopManager shopManager, EconomyManager economyManager, RewardGiver rewardGiver,
                        CustomItemManager customItemManager, RankManager rankManager, TalentManager talentManager,
                        PromotionManager promotionManager, TokenManager tokenManager, LoyaltyManager loyaltyManager,
                        MessageManager messages) {
        this.shopManager = shopManager;
        this.economyManager = economyManager;
        this.rewardGiver = rewardGiver;
        this.customItemManager = customItemManager;
        this.rankManager = rankManager;
        this.talentManager = talentManager;
        this.promotionManager = promotionManager;
        this.tokenManager = tokenManager;
        this.loyaltyManager = loyaltyManager;
        this.messages = messages;
    }

    public void buy(Player player, ShopManager.ShopItem item) {
        if (!item.isPurchasable()) {
            messages.send(player, "boutique.non-achetable");
            return;
        }

        List<String> toutesLesCles = shopManager.getAllItemKeysSorted();
        double reductionAuto = promotionManager.getReductionAutomatiquePourcent(player, item.categoryId(), item.id(), toutesLesCles);
        PromotionManager.PromoCode codeActif = promotionManager.getCodeActif(player.getUniqueId());
        double reductionCode = codeActif != null ? codeActif.reductionPourcent() : 0;
        double reductionTotale = Math.min(reductionAuto + reductionCode, promotionManager.getReductionMaxPourcent());
        double prixFinal = item.buyPrice() * (1.0 - reductionTotale / 100.0);

        if (!economyManager.withdraw(player.getUniqueId(), prixFinal)) {
            messages.send(player, "boutique.fonds-insuffisants");
            return;
        }
        rewardGiver.give(player, item.reward());
        promotionManager.marquerAchatEffectue(player.getUniqueId());
        if (codeActif != null) {
            promotionManager.enregistrerUtilisationCode(player.getUniqueId(), codeActif.code());
            promotionManager.retirerCodeActif(player.getUniqueId());
        }

        long jetonsGagnes = tokenManager.calculerCashback(prixFinal);
        if (jetonsGagnes > 0) {
            tokenManager.addJetons(player.getUniqueId(), jetonsGagnes);
        }

        int ancienPoints = loyaltyManager.getPoints(player.getUniqueId());
        int nouveauxPoints = loyaltyManager.addPointsForPurchase(player.getUniqueId(), prixFinal);
        awardNewLoyaltyTiersIfAny(player, ancienPoints, nouveauxPoints);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", item.displayName());
        placeholders.put("prix", economyManager.format(prixFinal));
        placeholders.put("jetons", String.valueOf(jetonsGagnes));
        if (reductionTotale > 0) {
            placeholders.put("reduction", formatPercent(reductionTotale));
            messages.send(player, "boutique.achat-reussi-reduction", placeholders);
        } else {
            messages.send(player, "boutique.achat-reussi", placeholders);
        }
    }

    /** Revend TOUT le stack en main du joueur, si celui-ci correspond a l'article (meme materiau
     * vanilla, ou meme id d'item custom). Rien n'est retire si aucune correspondance. */
    public void sell(Player player, ShopManager.ShopItem item) {
        if (!item.isSellable()) {
            messages.send(player, "boutique.non-vendable");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        int amount = matchingAmount(hand, item.reward());
        if (amount <= 0) {
            messages.send(player, "boutique.rien-a-vendre");
            return;
        }

        double bonusPercent = rankManager.getBonusVentePourcent(player.getUniqueId())
                + talentManager.getBonusVentePourcent(player.getUniqueId());
        double total = amount * item.sellPrice() * (1.0 + bonusPercent / 100.0);
        player.getInventory().setItemInMainHand(null);
        economyManager.deposit(player.getUniqueId(), total);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(amount));
        placeholders.put("item", item.displayName());
        placeholders.put("montant", economyManager.format(total));
        messages.send(player, "boutique.vente-reussie", placeholders);
    }

    public void activerCode(Player player, String code) {
        PromotionManager.PromoCode promoCode = promotionManager.getCode(code);
        if (promoCode == null) {
            messages.send(player, "boutique.code-invalide");
            return;
        }
        int utilisations = promotionManager.getUtilisationsCode(player.getUniqueId(), promoCode.code());
        if (utilisations >= promoCode.usagesMaxParJoueur()) {
            messages.send(player, "boutique.code-epuise");
            return;
        }
        promotionManager.activerCode(player, promoCode.code());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("code", promoCode.code());
        placeholders.put("reduction", formatPercent(promoCode.reductionPourcent()));
        messages.send(player, "boutique.code-active", placeholders);
    }

    public void convertirArgentEnJetons(Player player, double montantArgent) {
        double taux = tokenManager.getTauxArgentParJeton();
        long jetons = (long) Math.floor(montantArgent / taux);
        if (jetons <= 0) {
            messages.send(player, "boutique.conversion-montant-insuffisant");
            return;
        }
        double cout = jetons * taux;
        if (!economyManager.withdraw(player.getUniqueId(), cout)) {
            messages.send(player, "boutique.fonds-insuffisants");
            return;
        }
        tokenManager.addJetons(player.getUniqueId(), jetons);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("jetons", String.valueOf(jetons));
        placeholders.put("argent", economyManager.format(cout));
        messages.send(player, "boutique.conversion-vers-jetons", placeholders);
    }

    public void convertirJetonsEnArgent(Player player, long nombreJetons) {
        if (!tokenManager.removeJetons(player.getUniqueId(), nombreJetons)) {
            messages.send(player, "boutique.jetons-insuffisants");
            return;
        }
        double taux = tokenManager.getTauxArgentParJeton();
        double montant = nombreJetons * taux;
        economyManager.deposit(player.getUniqueId(), montant);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("jetons", String.valueOf(nombreJetons));
        placeholders.put("argent", economyManager.format(montant));
        messages.send(player, "boutique.conversion-vers-argent", placeholders);
    }

    /** Donne le(s) coffre-cadeau de chaque niveau de fidelite fraichement franchi par cet achat
     * (un gros achat peut faire sauter plusieurs niveaux d'un coup). */
    private void awardNewLoyaltyTiersIfAny(Player player, int ancienPoints, int nouveauxPoints) {
        if (nouveauxPoints <= ancienPoints) {
            return;
        }
        for (LoyaltyManager.LoyaltyTier tier : loyaltyManager.getTiers()) {
            if (tier.pointsRequis() > ancienPoints && tier.pointsRequis() <= nouveauxPoints
                    && !loyaltyManager.hasClaimedGift(player.getUniqueId(), tier.niveau())) {
                loyaltyManager.markGiftClaimed(player.getUniqueId(), tier.niveau());
                if (tier.cadeau() != null) {
                    rewardGiver.give(player, tier.cadeau());
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("niveau", tier.nom());
                placeholders.put("reduction", formatPercent(tier.reductionPourcent()));
                messages.send(player, "boutique.fidelite-niveau-atteint", placeholders);
            }
        }
    }

    public void afficherFidelite(Player player) {
        int points = loyaltyManager.getPoints(player.getUniqueId());
        LoyaltyManager.LoyaltyTier tierActuel = loyaltyManager.getTierForPoints(points);
        LoyaltyManager.LoyaltyTier prochainTier = loyaltyManager.getNextTier(points);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("points", String.valueOf(points));
        placeholders.put("niveau", tierActuel != null ? tierActuel.nom() : messages.raw("boutique.fidelite-aucun-niveau"));
        placeholders.put("reduction", tierActuel != null ? formatPercent(tierActuel.reductionPourcent()) : "0");
        messages.send(player, "boutique.fidelite-statut", placeholders);

        if (prochainTier != null) {
            Map<String, String> suivantPlaceholders = new HashMap<>();
            suivantPlaceholders.put("niveau", prochainTier.nom());
            suivantPlaceholders.put("points-manquants", String.valueOf(prochainTier.pointsRequis() - points));
            messages.send(player, "boutique.fidelite-prochain-niveau", suivantPlaceholders);
        } else if (tierActuel != null) {
            messages.send(player, "boutique.fidelite-niveau-max");
        }
    }

    public void afficherJetons(Player player) {
        long jetons = tokenManager.getJetons(player.getUniqueId());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("jetons", String.valueOf(jetons));
        placeholders.put("taux", economyManager.format(tokenManager.getTauxArgentParJeton()));
        messages.send(player, "boutique.jetons-solde", placeholders);
    }

    private int matchingAmount(ItemStack hand, Reward reward) {
        if (hand == null || hand.getType() == Material.AIR) {
            return 0;
        }
        if (reward.type() == RewardType.ITEM) {
            return reward.item() != null && hand.getType() == reward.item().getType() ? hand.getAmount() : 0;
        }
        if (reward.type() == RewardType.OBJET_CUSTOM) {
            String heldId = customItemManager.getCustomItemId(hand);
            return reward.customItemId() != null && reward.customItemId().equalsIgnoreCase(heldId) ? hand.getAmount() : 0;
        }
        return 0;
    }

    private String formatPercent(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }
}
