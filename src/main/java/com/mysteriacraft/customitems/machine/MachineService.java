package com.mysteriacraft.customitems.machine;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemDefinition;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.quests.QuestService;
import com.mysteriacraft.quests.QuestType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Traite l'utilisation de la Machine a Transformation :
 * - clic-droit avec un carburant (voir machine-transformation.carburants) en main -> ravitaille
 *   la machine (ajoute des charges et applique le cooldown de ce type de carburant) ;
 * - clic-droit avec l'objet d'amelioration en main -> augmente durablement la chance de reussite ;
 * - clic-droit a vide -> affiche l'etat (carburant restant, cooldown, bonus de reussite) ;
 * - clic-droit avec un minerai accepte en main -> tente la transformation si du carburant est
 *   disponible et que le cooldown (par machine) est ecoule ; le minerai est consomme dans tous
 *   les cas, avec "chance-reussite" (+ bonus) % de le transformer en Lucky Block. En cas
 *   d'echec, il est perdu.
 */
public class MachineService {

    private final Plugin plugin;
    private final MachineManager manager;
    private final CustomItemManager customItemManager;
    private final LuckyBlockManager luckyBlockManager;
    private final QuestService questService;
    private final MessageManager messages;

    /** Tache d'actionbar en cours par joueur, pour eviter d'en empiler plusieurs en parallele. */
    private final Map<UUID, BukkitTask> cooldownActionbarTasks = new ConcurrentHashMap<>();

    public MachineService(Plugin plugin, MachineManager manager, CustomItemManager customItemManager,
                           LuckyBlockManager luckyBlockManager, QuestService questService, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.customItemManager = customItemManager;
        this.luckyBlockManager = luckyBlockManager;
        this.questService = questService;
        this.messages = messages;
    }

    public void handleInteract(Player player, Block machineBlock) {
        ItemStack inHand = player.getInventory().getItemInMainHand();

        if (inHand.getType() == Material.AIR) {
            showStatus(player, machineBlock);
            return;
        }

        String customItemId = customItemManager.getCustomItemId(inHand);
        MachineManager.FuelType fuelType = manager.getFuelType(customItemId);
        if (fuelType != null) {
            refuel(player, machineBlock, inHand, fuelType);
            return;
        }

        if (customItemId != null && customItemId.equalsIgnoreCase(manager.getUpgradeItemId())) {
            upgrade(player, machineBlock, inHand);
            return;
        }

        attemptTransformation(player, machineBlock, inHand);
    }

    private void showStatus(Player player, Block machineBlock) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("carburant", String.valueOf(manager.getFuel(machineBlock)));
        placeholders.put("bonus", formatBonus(manager.getBonusReussite(machineBlock)));
        placeholders.put("chance", String.valueOf((int) manager.getEffectiveChance(machineBlock)));

        long remaining = manager.getRemainingCooldownMillis(machineBlock);
        if (remaining > 0) {
            placeholders.put("cooldown", formatDuration(remaining));
            messages.send(player, "machine.statut-en-attente", placeholders);
            startCooldownActionbar(player, machineBlock);
        } else {
            messages.send(player, "machine.statut-pret", placeholders);
        }
        updateHologram(machineBlock);
    }

    private void refuel(Player player, Block machineBlock, ItemStack fuelItem, MachineManager.FuelType fuelType) {
        int remaining = fuelItem.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(fuelItem, remaining) : null);

        int newTotal = manager.addFuel(machineBlock, fuelType.charges());
        manager.setActiveCooldownSeconds(machineBlock, fuelType.cooldownSeconds());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("charges", String.valueOf(fuelType.charges()));
        placeholders.put("total", String.valueOf(newTotal));
        placeholders.put("cooldown", formatDuration(fuelType.cooldownSeconds() * 1000L));
        messages.send(player, "machine.ravitaillee", placeholders);

        Location loc = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc, 15, 0.4, 0.4, 0.4);
        player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
        updateHologram(machineBlock);
    }

    private void upgrade(Player player, Block machineBlock, ItemStack upgradeItem) {
        double currentBonus = manager.getBonusReussite(machineBlock);
        if (currentBonus >= manager.getBonusMax()) {
            messages.send(player, "machine.amelioration-max");
            return;
        }

        int remaining = upgradeItem.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(upgradeItem, remaining) : null);

        double newBonus = manager.addBonusReussite(machineBlock, manager.getBonusPerUpgrade());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("bonus", formatBonus(newBonus));
        placeholders.put("chance", String.valueOf((int) manager.getEffectiveChance(machineBlock)));
        messages.send(player, "machine.amelioration-effectuee", placeholders);

        Location loc = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.5, 0.3, 0.02);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
        updateHologram(machineBlock);
    }

    private void attemptTransformation(Player player, Block machineBlock, ItemStack inHand) {
        String familyId = manager.getTargetFamily(inHand.getType());
        if (familyId == null) {
            messages.send(player, "machine.minerai-non-accepte");
            return;
        }

        if (manager.getFuel(machineBlock) <= 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("carburant", defaultFuelName());
            messages.send(player, "machine.sans-carburant", placeholders);
            return;
        }

        long remainingCooldown = manager.getRemainingCooldownMillis(machineBlock);
        if (remainingCooldown > 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("temps", formatDuration(remainingCooldown));
            messages.send(player, "machine.cooldown", placeholders);
            startCooldownActionbar(player, machineBlock);
            return;
        }

        LuckyBlockFamily family = luckyBlockManager.getFamily(familyId);
        if (family == null) {
            messages.send(player, "machine.famille-introuvable");
            return;
        }

        // Consomme exactement 1 exemplaire du minerai, 1 charge de carburant, et relance le cooldown.
        int remaining = inHand.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);
        manager.consumeCharge(machineBlock);
        manager.markUsedNow(machineBlock);

        Location effectLocation = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        double chance = manager.getEffectiveChance(machineBlock);
        boolean success = ThreadLocalRandom.current().nextDouble(100.0) < chance;

        if (success) {
            ItemStack reward = luckyBlockManager.createItem(family);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }

            effectLocation.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, effectLocation, 25, 0.4, 0.4, 0.4);
            player.playSound(effectLocation, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("caisse", family.displayName());
            messages.send(player, "machine.reussite", placeholders);

            questService.registerProgress(player, QuestType.MACHINE_TRANSFORM, family.id(), 1);
        } else {
            effectLocation.getWorld().spawnParticle(Particle.SMOKE_NORMAL, effectLocation, 20, 0.4, 0.4, 0.4);
            player.playSound(effectLocation, Sound.ENTITY_ITEM_BREAK, 1f, 0.7f);
            messages.send(player, "machine.echec");
        }
        updateHologram(machineBlock);
    }

    /**
     * Effet de particules ambiant + rafraichissement des hologrammes, appele periodiquement depuis
     * MysteriaCraft pour toutes les machines posees depuis le demarrage du plugin. La densite/couleur
     * des particules varie selon le niveau de carburant restant.
     */
    public void tickAmbientParticles() {
        List<Location> stale = new ArrayList<>();
        for (Location location : manager.getActiveMachineLocations()) {
            World world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            Block block = location.getBlock();
            if (!manager.isMachineBlock(block)) {
                stale.add(location);
                continue;
            }

            Location effectLocation = location.clone().add(0.5, 1.1, 0.5);
            int fuel = manager.getFuel(block);
            if (fuel <= 0) {
                world.spawnParticle(Particle.SMOKE_NORMAL, effectLocation, 1, 0.1, 0.1, 0.1, 0.0);
            } else if (fuel < 5) {
                world.spawnParticle(Particle.FLAME, effectLocation, 1, 0.15, 0.1, 0.15, 0.0);
            } else {
                world.spawnParticle(Particle.END_ROD, effectLocation, 2, 0.2, 0.15, 0.2, 0.0);
            }
            updateHologram(block);
        }
        stale.forEach(manager::forgetMachine);
    }

    /** Met a jour le texte de l'hologramme de cette machine (charges, chance, cooldown restant). */
    private void updateHologram(Block machineBlock) {
        ArmorStand stand = manager.getHologram(machineBlock);
        if (stand == null) {
            return;
        }
        int fuel = manager.getFuel(machineBlock);
        int chance = (int) manager.getEffectiveChance(machineBlock);
        long remaining = manager.getRemainingCooldownMillis(machineBlock);
        String etat = remaining > 0 ? "&c" + formatDuration(remaining) : "&aPrete";

        stand.setCustomName(MessageManager.color(
                "&b&lMachine &7| &e" + fuel + " carburant &7| &e" + chance + "% &7| " + etat));
    }

    /** Envoie un compte a rebours en actionbar tant que le cooldown de cette machine n'est pas ecoule. */
    private void startCooldownActionbar(Player player, Block machineBlock) {
        UUID uuid = player.getUniqueId();
        BukkitTask existing = cooldownActionbarTasks.get(uuid);
        if (existing != null) {
            existing.cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long remaining = manager.getRemainingCooldownMillis(machineBlock);
            if (!player.isOnline() || remaining <= 0 || !manager.isMachineBlock(machineBlock)) {
                BukkitTask self = cooldownActionbarTasks.remove(uuid);
                if (self != null) {
                    self.cancel();
                }
                return;
            }
            player.sendActionBar(LegacyComponentSerializer.legacySection()
                    .deserialize(MessageManager.color("&c&lMachine en recharge : &e" + formatDuration(remaining))));
        }, 0L, 20L);

        cooldownActionbarTasks.put(uuid, task);
    }

    private String defaultFuelName() {
        MachineManager.FuelType defaultFuel = manager.getDefaultFuelType();
        if (defaultFuel == null) {
            return "carburant";
        }
        CustomItemDefinition definition = customItemManager.getItem(defaultFuel.itemId());
        return definition != null ? definition.displayName() : defaultFuel.itemId();
    }

    private String formatBonus(double bonus) {
        return bonus == Math.floor(bonus) ? String.valueOf((int) bonus) : String.valueOf(bonus);
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    /** Formate une duree en millisecondes en "XhYmZs" (n'affiche que les unites non nulles). */
    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000);
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
}
