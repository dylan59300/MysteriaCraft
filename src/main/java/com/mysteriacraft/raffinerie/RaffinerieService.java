package com.mysteriacraft.raffinerie;

import com.mysteriacraft.core.config.MessageManager;
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

/** Traite le clic-droit sur un bloc de Raffinerie : consomme 1 minerai brut en main, donne 1
 * lingot (+1 bonus selon chance-bonus-pourcent, eventuellement boostee par le metier Forgeron). */
public class RaffinerieService {

    private final RaffinerieManager manager;
    private final MessageManager messages;

    public RaffinerieService(RaffinerieManager manager, MessageManager messages) {
        this.manager = manager;
        this.messages = messages;
    }

    public void handleInteract(Player player, Block block) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        Material output = manager.getOutput(inHand.getType());
        if (output == null) {
            messages.send(player, "raffinerie.minerai-non-accepte");
            return;
        }

        int remaining = inHand.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);

        int amount = 1;
        boolean bonus = ThreadLocalRandom.current().nextDouble(100.0) < manager.getChanceBonusPourcent();
        if (bonus) {
            amount = 2;
        }

        ItemStack result = new ItemStack(output, amount);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(result);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(amount));
        placeholders.put("objet", output.name().replace('_', ' '));
        messages.send(player, bonus ? "raffinerie.raffine-bonus" : "raffinerie.raffine", placeholders);

        Location loc = block.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(bonus ? Particle.FLAME : Particle.SMOKE_NORMAL, loc, 15, 0.3, 0.3, 0.3);
        player.playSound(loc, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 1f, 1f);
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
