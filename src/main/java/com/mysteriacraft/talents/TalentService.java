package com.mysteriacraft.talents;

import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Traite le gain de points (1 par kill) et le deblocage des noeuds de l'arbre de talents, et
 * (re)applique les bonus passifs permanents (degats, vie max, vitesse de minage) sur le joueur. */
public class TalentService {

    private static final UUID DEGATS_MODIFIER_UUID = UUID.fromString("c2d3e4f5-a6b7-4c8d-9e0f-1a2b3c4d5e6f");
    private static final UUID VIE_MODIFIER_UUID = UUID.fromString("d3e4f5a6-b7c8-4d9e-0f1a-2b3c4d5e6f7a");

    private final Plugin plugin;
    private final TalentManager manager;
    private final MessageManager messages;

    public TalentService(Plugin plugin, TalentManager manager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    public void gainPoint(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> manager.addPoints(uuid, 1));
    }

    public void unlock(Player player, String nodeId) {
        UUID uuid = player.getUniqueId();
        TalentManager.TalentNode node = manager.getNode(nodeId);
        if (node == null) {
            messages.send(player, "talents.introuvable");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = manager.unlock(uuid, nodeId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!success) {
                    messages.send(player, "talents.deblocage-impossible");
                    return;
                }
                applyPassiveBonuses(player);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("talent", node.nom());
                messages.send(player, "talents.debloque", placeholders);
            });
        });
    }

    /** (Re)applique les bonus passifs cumules (degats, vie max, vitesse de minage) sur ce joueur.
     * A appeler a la connexion et apres chaque deblocage. Fait une lecture SQLite synchrone :
     * a appeler hors du thread principal si non deja fait, ou avec parcimonie (connexion/deblocage
     * uniquement). */
    public void applyPassiveBonuses(Player player) {
        UUID uuid = player.getUniqueId();
        applyModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, DEGATS_MODIFIER_UUID, manager.getBonusDegats(uuid));
        applyModifier(player, Attribute.GENERIC_MAX_HEALTH, VIE_MODIFIER_UUID, manager.getBonusVieMax(uuid));

        int niveauMinage = manager.getNiveauVitesseMinage(uuid);
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        if (niveauMinage > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING,
                    PotionEffect.INFINITE_DURATION, niveauMinage - 1, true, false));
        }
    }

    private void applyModifier(Player player, Attribute attribute, UUID modifierUuid, double valeur) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.getModifiers().stream()
                .filter(modifier -> modifier.getUniqueId().equals(modifierUuid))
                .forEach(instance::removeModifier);
        if (valeur > 0) {
            instance.addModifier(new AttributeModifier(modifierUuid, "mysteriacraft-talent",
                    valeur, AttributeModifier.Operation.ADD_NUMBER));
        }
    }
}
