package com.mysteriacraft.marchenoir;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/** Traite l'achat au Marche Noir : verifie que l'objet est toujours dans la rotation active
 * (le prix affiche a l'ouverture du menu peut avoir change entre-temps si le creneau a tourne). */
public class MarcheNoirService {

    private final MarcheNoirManager manager;
    private final EconomyManager economyManager;
    private final RewardGiver rewardGiver;
    private final MessageManager messages;

    public MarcheNoirService(MarcheNoirManager manager, EconomyManager economyManager,
                              RewardGiver rewardGiver, MessageManager messages) {
        this.manager = manager;
        this.economyManager = economyManager;
        this.rewardGiver = rewardGiver;
        this.messages = messages;
    }

    public void acheter(Player player, String offreId) {
        MarcheNoirManager.OffreActive offre = manager.getOffre(offreId);
        if (offre == null) {
            messages.send(player, "marchenoir.offre-expiree");
            return;
        }
        if (!economyManager.withdraw(player.getUniqueId(), offre.prix())) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("prix", economyManager.format(offre.prix()));
            messages.send(player, "marchenoir.fonds-insuffisants", placeholders);
            return;
        }
        rewardGiver.give(player, offre.reward());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", offre.reward().displayName());
        placeholders.put("prix", economyManager.format(offre.prix()));
        messages.send(player, "marchenoir.achat-reussi", placeholders);
    }
}
