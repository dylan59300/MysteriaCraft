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
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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
 * - clic-droit avec au moins MINERAIS_PAR_TRANSFORMATION minerais acceptes en main -> tente la
 *   transformation si du carburant est disponible et que le cooldown (par machine) est ecoule ;
 *   le lot de minerais est consomme dans tous les cas (1 seule charge de carburant, 1 seul jet de
 *   chance pour tout le lot), avec "chance-reussite" (+ bonus) % de le transformer en Lucky Block.
 *   En cas d'echec, il est perdu.
 */
public class MachineService {

    /** Quantite de minerai consommee pour UNE transformation (= 1 charge de carburant, 1 seul jet de chance). */
    private static final int MINERAIS_PAR_TRANSFORMATION = 10;

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

        MachineManager.MachineTier tierFromKit = manager.getTierForKitItem(customItemId);
        if (tierFromKit != null) {
            upgradeTier(player, machineBlock, inHand, tierFromKit);
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

    /**
     * Fait passer la machine au tier suivant en utilisant un kit d'amelioration structurelle.
     * Le kit doit correspondre exactement au tier qui suit le tier ACTUEL de la machine : un kit
     * "Or" ne fonctionne pas sur une machine encore au tier Bronze, il faut d'abord passer par
     * "Argent". Change le materiau du bloc et sa chance/cooldown de base ; le carburant et le
     * bonus d'amelioration deja accumules sont conserves.
     */
    private void upgradeTier(Player player, Block machineBlock, ItemStack kitItem, MachineManager.MachineTier kitTargetTier) {
        MachineManager.MachineTier currentTier = manager.getBlockTier(machineBlock);
        MachineManager.MachineTier expectedNextTier = manager.getNextTier(currentTier.id());

        if (expectedNextTier == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("tier", currentTier.displayName());
            messages.send(player, "machine.tier-max", placeholders);
            return;
        }
        if (!expectedNextTier.id().equalsIgnoreCase(kitTargetTier.id())) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("tier-actuel", currentTier.displayName());
            placeholders.put("tier-suivant", expectedNextTier.displayName());
            messages.send(player, "machine.tier-mauvais-kit", placeholders);
            return;
        }

        int remaining = kitItem.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(kitItem, remaining) : null);

        manager.setTier(machineBlock, expectedNextTier);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("tier", expectedNextTier.displayName());
        placeholders.put("chance", String.valueOf((int) manager.getEffectiveChance(machineBlock)));
        placeholders.put("cooldown", formatDuration(expectedNextTier.cooldownSeconds() * 1000L));
        messages.send(player, "machine.tier-ameliore", placeholders);

        Location loc = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.TOTEM, loc, 40, 0.4, 0.5, 0.4, 0.1);
        loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 1f, 1.2f);
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

        if (inHand.getAmount() < MINERAIS_PAR_TRANSFORMATION) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("quantite", String.valueOf(MINERAIS_PAR_TRANSFORMATION));
            placeholders.put("minerai", inHand.getType().name());
            messages.send(player, "machine.minerai-insuffisant", placeholders);
            return;
        }

        // Consomme MINERAIS_PAR_TRANSFORMATION exemplaires du minerai (1 seul jet de chance pour le lot)
        // et relance le cooldown. Le carburant n'est consomme tout de suite que si le mode economique
        // (carburant-uniquement-si-echec) est desactive.
        int remaining = inHand.getAmount() - MINERAIS_PAR_TRANSFORMATION;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);
        boolean economyMode = manager.isConsumeFuelOnFailureOnly();
        if (!economyMode) {
            manager.consumeCharge(machineBlock);
        }
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
            if (economyMode) {
                manager.consumeCharge(machineBlock);
            }
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
            tickAutoFeed(block);
        }
        stale.forEach(manager::forgetMachine);
    }

    /**
     * Auto-alimentation : si un/des conteneur(s) sont colles a la machine, pioche automatiquement
     * MINERAIS_PAR_TRANSFORMATION minerais acceptes dans le conteneur d'entree des que le cooldown
     * est ecoule (et qu'il reste du carburant), et depose le Lucky Block obtenu (en cas de reussite)
     * dans le conteneur de sortie. Purement automatique, aucun joueur n'est implique. Si aucun
     * minerai accepte n'est trouve en quantite suffisante dans l'entree, un petit indicateur
     * (poudre rouge) apparait au-dessus pour signaler qu'il faut la reapprovisionner.
     */
    private void tickAutoFeed(Block machineBlock) {
        if (!manager.isAutoAlimentationEnabled()) {
            return;
        }
        if (manager.getFuel(machineBlock) <= 0 || manager.getRemainingCooldownMillis(machineBlock) > 0) {
            return;
        }
        MachineManager.AdjacentContainers containers = manager.getAdjacentContainers(machineBlock);
        if (containers == null) {
            return;
        }
        if (!(containers.input().getState() instanceof Container inputContainer)
                || !(containers.output().getState() instanceof Container outputContainer)) {
            return;
        }

        Inventory inputInventory = inputContainer.getInventory();
        Inventory outputInventory = outputContainer.getInventory();

        // Parcourt les minerais acceptes dans leur ORDRE DE PRIORITE (celui declare dans
        // machine-transformation.minerais), pas l'ordre des cases du coffre : le premier minerai
        // prioritaire present dans l'entree est traite en premier, quelle que soit sa case.
        // Ne retient que les piles d'au moins MINERAIS_PAR_TRANSFORMATION minerais : impossible de lancer
        // une transformation partielle depuis un conteneur, comme pour le clic-droit manuel.
        Material match = null;
        int slot = -1;
        for (Material candidate : manager.getAcceptedMaterials()) {
            int candidateSlot = inputInventory.first(candidate);
            if (candidateSlot >= 0 && inputInventory.getItem(candidateSlot).getAmount() >= MINERAIS_PAR_TRANSFORMATION) {
                match = candidate;
                slot = candidateSlot;
                break;
            }
        }

        if (match == null) {
            // Aucun minerai accepte (en quantite suffisante) trouve dans l'entree : petit indicateur
            // visuel de reapprovisionnement.
            Location warningLocation = containers.input().getLocation().add(0.5, 1.1, 0.5);
            containers.input().getWorld().spawnParticle(Particle.REDSTONE, warningLocation, 6, 0.2, 0.1, 0.2, 0.0,
                    new Particle.DustOptions(Color.RED, 1.2f));
            return;
        }

        String familyId = manager.getTargetFamily(match);
        LuckyBlockFamily family = familyId != null ? luckyBlockManager.getFamily(familyId) : null;
        if (family == null) {
            return;
        }

        ItemStack stack = inputInventory.getItem(slot);
        int remainingInStack = stack.getAmount() - MINERAIS_PAR_TRANSFORMATION;
        if (remainingInStack > 0) {
            stack.setAmount(remainingInStack);
            inputInventory.setItem(slot, stack);
        } else {
            inputInventory.setItem(slot, null);
        }

        boolean economyMode = manager.isConsumeFuelOnFailureOnly();
        if (!economyMode) {
            manager.consumeCharge(machineBlock);
        }
        manager.markUsedNow(machineBlock);

        Location effectLocation = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        boolean success = ThreadLocalRandom.current().nextDouble(100.0) < manager.getEffectiveChance(machineBlock);
        if (success) {
            ItemStack reward = luckyBlockManager.createItem(family);
            Map<Integer, ItemStack> leftovers = outputInventory.addItem(reward);
            leftovers.values().forEach(leftover ->
                    containers.output().getWorld().dropItemNaturally(containers.output().getLocation(), leftover));
            playAutoFeedEffects(effectLocation, true);
        } else {
            if (economyMode) {
                manager.consumeCharge(machineBlock);
            }
            playAutoFeedEffects(effectLocation, false);
        }
        updateHologram(machineBlock);
    }

    private void playAutoFeedEffects(Location location, boolean success) {
        World world = location.getWorld();
        if (success) {
            world.spawnParticle(Particle.VILLAGER_HAPPY, location, 25, 0.4, 0.4, 0.4);
            world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        } else {
            world.spawnParticle(Particle.SMOKE_NORMAL, location, 20, 0.4, 0.4, 0.4);
            world.playSound(location, Sound.ENTITY_ITEM_BREAK, 1f, 0.7f);
        }
    }

    /** Met a jour le texte de l'hologramme de cette machine. En mode normal : nom, jauge de
     * carburant, % de reussite, barre de cooldown et faces d'entree/sortie de l'auto-alimentation
     * si des conteneurs sont colles. En mode compact (hologramme-compact: true) : uniquement la
     * barre de cooldown et le % de reussite. */
    private void updateHologram(Block machineBlock) {
        ArmorStand stand = manager.getHologram(machineBlock);
        if (stand == null) {
            return;
        }
        int chance = (int) manager.getEffectiveChance(machineBlock);
        long remaining = manager.getRemainingCooldownMillis(machineBlock);
        int segments = manager.getHologramSegments();
        String cooldownBar = remaining > 0
                ? cooldownProgressBar(remaining, machineBlock, segments) + " &f" + formatDuration(remaining)
                : "&a[" + "■".repeat(segments) + "] &aPrete";

        if (manager.isHologramCompact()) {
            stand.setCustomName(MessageManager.color(cooldownBar + " &7| &e" + chance + "%"));
            return;
        }

        MachineManager.MachineTier tier = manager.getBlockTier(machineBlock);
        int fuel = manager.getFuel(machineBlock);
        String fuelBar = fuelGaugeBar(fuel, tier);
        String ligne1 = "&b&lMachine &7[" + tier.displayName() + "&7] &7| &e" + fuel + " " + fuelBar
                + " &7| &e" + chance + "% &7| " + cooldownBar;
        stand.setCustomName(MessageManager.color(ligne1 + autoFeedSuffix(machineBlock)));
    }

    /** Barre "[■■■□□]" coloree du rouge (debut) au vert (fin) selon l'avancement du cooldown. */
    private String cooldownProgressBar(long remainingMillis, Block machineBlock, int segments) {
        long totalMillis = manager.getActiveCooldownSeconds(machineBlock) * 1000L;
        double progress = totalMillis > 0
                ? 1.0 - Math.min(1.0, Math.max(0.0, (double) remainingMillis / totalMillis))
                : 1.0;
        int filled = Math.max(0, Math.min(segments, (int) Math.round(progress * segments)));

        String filledColor = progress < 0.34 ? "&c" : progress < 0.67 ? "&6" : "&a";
        return "&f[" + filledColor + "■".repeat(filled) + "&7" + "□".repeat(segments - filled) + "&f]";
    }

    /** Petite jauge "[■■□□□]" (5 segments fixes, bleue) indiquant le niveau de carburant par
     * rapport a la jauge-carburant-max du tier actuel (purement visuel, ne plafonne pas le stock reel). */
    private String fuelGaugeBar(int fuel, MachineManager.MachineTier tier) {
        int segments = 5;
        double progress = Math.min(1.0, (double) fuel / tier.fuelGaugeMax());
        int filled = Math.max(0, Math.min(segments, (int) Math.round(progress * segments)));
        return "&f[&b" + "■".repeat(filled) + "&7" + "□".repeat(segments - filled) + "&f]";
    }

    /** "&7| &aEntree: Nord &7| &6Sortie: Sud" (ou juste "Entree/Sortie: Nord" si un seul conteneur colle), vide sinon. */
    private String autoFeedSuffix(Block machineBlock) {
        MachineManager.AdjacentContainers containers = manager.getAdjacentContainers(machineBlock);
        if (containers == null) {
            return "";
        }
        String inputLabel = MachineManager.faceLabel(containers.inputFace());
        if (containers.inputFace() == containers.outputFace()) {
            return " &7| &bE/S: " + inputLabel;
        }
        String outputLabel = MachineManager.faceLabel(containers.outputFace());
        return " &7| &aEntree: " + inputLabel + " &7| &6Sortie: " + outputLabel;
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
            String bar = cooldownProgressBar(remaining, machineBlock, manager.getHologramSegments());
            player.sendActionBar(LegacyComponentSerializer.legacySection()
                    .deserialize(MessageManager.color("&c&lMachine en recharge : " + bar + " &e" + formatDuration(remaining))));
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
