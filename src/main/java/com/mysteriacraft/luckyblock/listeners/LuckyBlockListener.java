package com.mysteriacraft.luckyblock.listeners;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.LuckyBlockService;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Marque un bloc pose comme Lucky Block s'il provient d'un item marque (craft/achat/recompense).
 * Gere aussi le bonus de minerais : poser un minerai a cote d'un Lucky Block (ou l'inverse)
 * augmente sa chance d'effet BON, selon le minerai (voir bonus-minerais dans luckyblocks.yml).
 * Un Lucky Block pose N'EST PLUS CASSABLE en survie (reste en place indefiniment) : un clic-droit
 * dessus tire et applique directement un effet, sans consommer ni endommager le bloc (voir
 * LuckyBlockService#handleRightClick). Les admins en mode creatif peuvent toujours le retirer.
 */
public class LuckyBlockListener implements Listener {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final LuckyBlockManager manager;
    private final LuckyBlockService service;
    private final MessageManager messages;

    public LuckyBlockListener(LuckyBlockManager manager, LuckyBlockService service, MessageManager messages) {
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        String familyId = manager.getFamilyIdFromItem(event.getItemInHand());

        if (familyId != null) {
            // Un Lucky Block est pose : on marque le bloc et on recupere le bonus des minerais deja voisins.
            manager.tagBlock(placed, familyId);
            double surroundingBonus = manager.computeSurroundingOreBonus(placed);
            if (surroundingBonus > 0) {
                manager.addBonus(placed, surroundingBonus);
            }
            return;
        }

        if (!manager.isBonusOre(placed.getType())) {
            return;
        }

        // Un minerai bonus est pose : on cherche un Lucky Block voisin pour lui donner le bonus.
        double oreBonus = manager.getOreBonus(placed.getType());
        for (BlockFace face : FACES) {
            Block neighbor = placed.getRelative(face);
            LuckyBlockFamily family = manager.getFamilyOfBlock(neighbor);
            if (family == null) {
                continue;
            }
            double newTotal = manager.addBonus(neighbor, oreBonus);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("bonus", String.valueOf((int) oreBonus));
            placeholders.put("total", String.valueOf((int) Math.min(newTotal, manager.getBonusMax())));
            placeholders.put("max", String.valueOf((int) manager.getBonusMax()));
            messages.send(event.getPlayer(), "luckyblock.bonus-augmente", placeholders);
        }
    }

    /** Clic-droit sur un Lucky Block DEJA POSE : tire et applique directement un effet (voir
     * LuckyBlockService#handleRightClick), sans passer par un menu d'apercu. Reutilisable
     * immediatement, le bloc n'est jamais consomme ni endommage par cette interaction.
     * IMPORTANT : si le joueur tient un item Lucky Block EN MAIN (il veut en poser un nouveau
     * contre le bloc clique, ex: empiler des Lucky Blocks), on laisse le placement vanilla se
     * faire normalement au lieu de declencher un effet. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractPlacedBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (manager.getFamilyIdFromItem(event.getItem()) != null) {
            return;
        }
        Block block = event.getClickedBlock();
        LuckyBlockFamily family = block != null ? manager.getFamilyOfBlock(block) : null;
        if (family == null) {
            return;
        }
        event.setCancelled(true);
        service.handleRightClick(event.getPlayer(), block, family);
    }

    /** Kit de connexion (voir luckyblocks.yml: kit-connexion) : donne "quantite" exemplaires de
     * CHAQUE famille de Lucky Block actuellement active, au maximum UNE FOIS PAR JOUR par joueur
     * (voir LuckyBlockManager#hasReceivedJoinKitToday). Une reconnexion le meme jour ne redonne rien. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!manager.isJoinKitEnabled() || manager.getJoinKitQuantity() <= 0) {
            return;
        }
        Player player = event.getPlayer();
        if (manager.hasReceivedJoinKitToday(player.getUniqueId())) {
            return;
        }
        int quantity = manager.getJoinKitQuantity();
        for (LuckyBlockFamily family : manager.getFamiliesSorted()) {
            ItemStack item = manager.createItem(family, quantity);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }
        manager.markJoinKitReceivedToday(player.getUniqueId());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(quantity));
        messages.send(player, "luckyblock.kit-connexion", placeholders);
    }

    /** Un Lucky Block pose ne se casse plus en survie (voir la classe). En creatif, un admin peut
     * toujours le retirer normalement (untagBlock nettoie son suivi en base). */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (manager.getFamilyOfBlock(block) == null) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            manager.untagBlock(block);
            return;
        }
        event.setCancelled(true);
    }
}
