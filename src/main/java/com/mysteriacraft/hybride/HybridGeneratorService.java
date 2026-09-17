package com.mysteriacraft.hybride;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Traite l'utilisation d'un Generateur Hybride : clic-droit recupere les DEUX ressources
 * accumulees d'un coup ; un conteneur colle a une face active l'auto-collecte des deux. */
public class HybridGeneratorService {

    private final HybridGeneratorManager manager;
    private final CustomItemManager customItemManager;
    private final MessageManager messages;

    public HybridGeneratorService(HybridGeneratorManager manager, CustomItemManager customItemManager, MessageManager messages) {
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.messages = messages;
    }

    public void handleInteract(Player player, Block block) {
        HybridGeneratorManager.GeneratorType type = manager.getBlockType(block);
        if (type == null) {
            messages.send(player, "generateurhybride.type-invalide");
            return;
        }

        int wholeA = manager.collectWholeA(block);
        int wholeB = manager.collectWholeB(block);
        if (wholeA <= 0 && wholeB <= 0) {
            messages.send(player, "generateurhybride.rien-a-recuperer");
            return;
        }

        List<String> recus = new ArrayList<>();
        if (wholeA > 0) {
            giveItems(player, type.sortieA(), wholeA);
            recus.add(wholeA + "x " + labelSortie(type.sortieA()));
        }
        if (wholeB > 0) {
            giveItems(player, type.sortieB(), wholeB);
            recus.add(wholeB + "x " + labelSortie(type.sortieB()));
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("objets", String.join(" &7+ &a", recus));
        messages.send(player, "generateurhybride.recupere", placeholders);

        Location loc = block.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 20, 0.4, 0.4, 0.4);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void giveItems(Player player, HybridGeneratorManager.Sortie sortie, int amount) {
        ItemStack drop = createStack(sortie, amount);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(drop);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private ItemStack createStack(HybridGeneratorManager.Sortie sortie, int amount) {
        if (sortie.customItemId() != null) {
            var definition = customItemManager.getItem(sortie.customItemId());
            if (definition != null) {
                return customItemManager.createItem(definition, amount);
            }
        }
        return new ItemStack(sortie.materiau(), amount);
    }

    private String labelSortie(HybridGeneratorManager.Sortie sortie) {
        if (sortie.customItemId() != null) {
            var definition = customItemManager.getItem(sortie.customItemId());
            if (definition != null) {
                return definition.displayName();
            }
        }
        return sortie.materiau().name().replace('_', ' ');
    }

    public void tickGenerators() {
        List<Location> stale = new ArrayList<>();
        for (Location location : manager.getActiveGeneratorLocations()) {
            World world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            Block block = location.getBlock();
            if (!manager.isGeneratorBlock(block)) {
                stale.add(location);
                continue;
            }
            manager.accrue(block);

            Block containerBlock = manager.getOutputContainer(block);
            if (containerBlock != null && containerBlock.getState() instanceof Container container) {
                autoCollect(block, container);
            }
        }
        stale.forEach(manager::forgetGenerator);
    }

    private void autoCollect(Block block, Container container) {
        HybridGeneratorManager.GeneratorType type = manager.getBlockType(block);
        if (type == null) {
            return;
        }
        int wholeA = manager.collectWholeA(block);
        if (wholeA > 0) {
            Map<Integer, ItemStack> leftovers = container.getInventory().addItem(createStack(type.sortieA(), wholeA));
            if (!leftovers.isEmpty()) {
                int refused = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                manager.addStockA(block, refused);
            }
        }
        int wholeB = manager.collectWholeB(block);
        if (wholeB > 0) {
            Map<Integer, ItemStack> leftovers = container.getInventory().addItem(createStack(type.sortieB(), wholeB));
            if (!leftovers.isEmpty()) {
                int refused = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                manager.addStockB(block, refused);
            }
        }
    }
}
