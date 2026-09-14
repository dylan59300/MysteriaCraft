package com.mysteriacraft.customitems.machine;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Traite l'utilisation de la Machine a Transformation : clic-droit sur le bloc avec un minerai
 * accepte en main -> le minerai est consomme, avec "chance-reussite" % de le transformer en
 * Lucky Block de la famille configuree pour ce minerai. En cas d'echec, le minerai est perdu.
 */
public class MachineService {

    private final MachineManager manager;
    private final LuckyBlockManager luckyBlockManager;
    private final MessageManager messages;

    public MachineService(MachineManager manager, LuckyBlockManager luckyBlockManager, MessageManager messages) {
        this.manager = manager;
        this.luckyBlockManager = luckyBlockManager;
        this.messages = messages;
    }

    public void attemptTransformation(Player player, Block machineBlock) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getType() == Material.AIR) {
            messages.send(player, "machine.aucun-item");
            return;
        }

        String familyId = manager.getTargetFamily(inHand.getType());
        if (familyId == null) {
            messages.send(player, "machine.minerai-non-accepte");
            return;
        }

        LuckyBlockFamily family = luckyBlockManager.getFamily(familyId);
        if (family == null) {
            messages.send(player, "machine.famille-introuvable");
            return;
        }

        // Consomme exactement 1 exemplaire du minerai en main.
        int remaining = inHand.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);

        Location effectLocation = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        boolean success = ThreadLocalRandom.current().nextDouble(100.0) < manager.getSuccessChance();

        if (success) {
            ItemStack reward = luckyBlockManager.createItem(family);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }

            effectLocation.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLocation, 25, 0.4, 0.4, 0.4);
            player.playSound(effectLocation, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("caisse", family.displayName());
            messages.send(player, "machine.reussite", placeholders);
        } else {
            effectLocation.getWorld().spawnParticle(Particle.SMOKE_NORMAL, effectLocation, 20, 0.4, 0.4, 0.4);
            player.playSound(effectLocation, Sound.ENTITY_ITEM_BREAK, 1f, 0.7f);
            messages.send(player, "machine.echec");
        }
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
