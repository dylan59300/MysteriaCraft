package com.mysteriacraft.customitems.machine.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.machine.MachineManager;
import com.mysteriacraft.customitems.machine.MachineService;
import com.mysteriacraft.customitems.machine.gui.MachineStatsGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /machine give <joueur> [quantite] | preset | reload | doubleloot <secondes> |
 * simulate <minerai> <nombre> (admin uniquement)
 * /machine stats | info (ouvert a tous les joueurs)
 */
public class MachineCommand implements CommandExecutor {

    private final ConfigManager customItemsConfig;
    private final MachineManager manager;
    private final MachineService service;
    private final MessageManager messages;

    public MachineCommand(ConfigManager customItemsConfig, MachineManager manager, MachineService service,
                           MessageManager messages) {
        this.customItemsConfig = customItemsConfig;
        this.manager = manager;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "machine.usage");
            return true;
        }

        // Ouvert a tous les joueurs (pas de permission admin) : affiche les stats du joueur lui-meme.
        if (args[0].equalsIgnoreCase("stats")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.commande-joueur-uniquement");
                return true;
            }
            new MachineStatsGui(player, manager, messages).open();
            return true;
        }

        // Ouvert a tous les joueurs : liste les minerais acceptes et leur famille cible.
        if (args[0].equalsIgnoreCase("info")) {
            sendInfo(sender);
            return true;
        }

        if (!sender.hasPermission("mysteriacraft.machine.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            customItemsConfig.reload();
            manager.loadConfig();
            messages.send(sender, "machine.reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length < 2) {
                messages.send(sender, "machine.usage");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages.send(sender, "general.joueur-introuvable");
                return true;
            }
            int quantity = 1;
            if (args.length >= 3) {
                try {
                    quantity = Math.max(1, Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    messages.send(sender, "machine.usage");
                    return true;
                }
            }

            ItemStack item = manager.createMachineItem(quantity);
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            }

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("joueur", target.getName());
            placeholders.put("quantite", String.valueOf(quantity));
            messages.send(sender, "machine.give-effectue", placeholders);
            return true;
        }

        if (args[0].equalsIgnoreCase("preset")) {
            return handlePreset(sender);
        }

        if (args[0].equalsIgnoreCase("doubleloot")) {
            if (args.length < 2) {
                messages.send(sender, "machine.usage");
                return true;
            }
            long seconds;
            try {
                seconds = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                messages.send(sender, "machine.usage");
                return true;
            }
            if (seconds <= 0) {
                messages.send(sender, "machine.usage");
                return true;
            }
            manager.activateDoubleLoot(seconds);
            Bukkit.broadcastMessage(MessageManager.color(
                    messages.raw("machine.doubleloot-annonce").replace("{duree}", formatDuration(seconds * 1000L))));
            return true;
        }

        if (args[0].equalsIgnoreCase("simulate")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.commande-joueur-uniquement");
                return true;
            }
            if (args.length < 3) {
                messages.send(sender, "machine.usage");
                return true;
            }
            Material ore = Material.matchMaterial(args[1]);
            if (ore == null) {
                messages.send(sender, "machine.minerai-non-accepte");
                return true;
            }
            int count;
            try {
                count = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                messages.send(sender, "machine.usage");
                return true;
            }
            service.simulate(player, ore, count);
            return true;
        }

        messages.send(sender, "machine.usage");
        return true;
    }

    /** Liste tous les minerais acceptes par la machine et leur famille cible, ouvert a tous. */
    private void sendInfo(CommandSender sender) {
        List<Material> materials = manager.getAcceptedMaterials();
        if (materials.isEmpty()) {
            messages.send(sender, "machine.info-vide");
            return;
        }
        messages.send(sender, "machine.info-titre");
        for (Material material : materials) {
            String familyId = manager.getTargetFamily(material);
            sender.sendMessage(MessageManager.color("&7- &e" + material.name() + " &7-> &6" + familyId));
        }
    }

    /** Formate une duree en millisecondes en "XhYmZs" (n'affiche que les unites non nulles). */
    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours).append("h");
        }
        if (minutes > 0) {
            builder.append(minutes).append("m");
        }
        if (hours == 0 && (seconds > 0 || builder.isEmpty())) {
            builder.append(seconds).append("s");
        }
        return builder.toString();
    }

    /**
     * Pose directement, devant le joueur, une Machine a Transformation deja configuree avec un
     * coffre d'entree et un coffre de sortie de part et d'autre (usine prete a l'emploi). Echoue
     * proprement si l'un des 3 emplacements n'est pas libre.
     */
    private boolean handlePreset(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        BlockFace facing = player.getFacing();
        if (facing != BlockFace.NORTH && facing != BlockFace.SOUTH
                && facing != BlockFace.EAST && facing != BlockFace.WEST) {
            facing = BlockFace.SOUTH;
        }

        Block machineBlock = player.getLocation().getBlock().getRelative(facing);
        Block inputBlock = machineBlock.getRelative(rotateLeft(facing));
        Block outputBlock = machineBlock.getRelative(rotateRight(facing));

        if (machineBlock.getType() != Material.AIR || inputBlock.getType() != Material.AIR
                || outputBlock.getType() != Material.AIR) {
            messages.send(sender, "machine.preset-obstrue");
            return true;
        }

        // tagBlock() place le bloc au tier de base (materiau + PDC + hologramme).
        manager.tagBlock(machineBlock);
        inputBlock.setType(Material.CHEST);
        outputBlock.setType(Material.CHEST);

        messages.send(sender, "machine.preset-installee");
        return true;
    }

    private BlockFace rotateLeft(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.WEST;
        };
    }

    private BlockFace rotateRight(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }
}
