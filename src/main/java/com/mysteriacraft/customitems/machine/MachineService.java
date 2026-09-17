package com.mysteriacraft.customitems.machine;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.customitems.CustomItemDefinition;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.customitems.generator.GeneratorManager;
import com.mysteriacraft.customitems.miningmachine.MiningMachineManager;
import com.mysteriacraft.luckyblock.LuckyBlockFamily;
import com.mysteriacraft.luckyblock.LuckyBlockManager;
import com.mysteriacraft.luckyblock.generator.LuckyBlockGeneratorManager;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Traite l'utilisation de la Machine a Transformation :
 * - clic-droit avec un carburant (voir machine-transformation.carburants) en main -> ravitaille
 *   la machine (ajoute des charges et applique le cooldown de ce type de carburant) ;
 * - clic-droit avec l'objet d'amelioration en main -> augmente durablement la chance-luckyblock ;
 * - clic-droit a vide -> affiche l'etat (carburant restant, cooldown, chance-luckyblock) ;
 * - clic-droit avec un minerai accepte en main -> echange 1 minerai contre 1 loot des que du
 *   carburant est disponible et que le cooldown (par machine) est ecoule : TOUJOURS une
 *   recompense, jamais d'echec ni de perte. "chance-luckyblock" (+ bonus) % de chance d'obtenir
 *   le Lucky Block de la famille ciblee ; le reste du temps, un item custom aleatoire pioche
 *   parmi TOUS ceux charges depuis custom_items.yml.
 */
public class MachineService {

    private final Plugin plugin;
    private final MachineManager manager;
    private final CustomItemManager customItemManager;
    private final LuckyBlockManager luckyBlockManager;
    private final QuestService questService;
    private final MessageManager messages;

    /** Injectes apres coup (setters, voir MysteriaCraft#setupCustomItems/setupGenerators) : ces
     * modules n'existent pas encore au moment ou MachineService est construit. Peuvent rester null
     * un court instant au demarrage ; pickRandomLoot() les ignore tant qu'ils ne sont pas definis. */
    private GeneratorManager generatorManager;
    private MiningMachineManager miningMachineManager;
    private LuckyBlockGeneratorManager luckyBlockGeneratorManager;

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

    public void setGeneratorManager(GeneratorManager generatorManager) {
        this.generatorManager = generatorManager;
    }

    public void setMiningMachineManager(MiningMachineManager miningMachineManager) {
        this.miningMachineManager = miningMachineManager;
    }

    public void setLuckyBlockGeneratorManager(LuckyBlockGeneratorManager luckyBlockGeneratorManager) {
        this.luckyBlockGeneratorManager = luckyBlockGeneratorManager;
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

        // Apercu (sneak + clic-droit) : montre la famille ciblee et la chance actuelle SANS rien
        // consommer ni lancer de jet de chance. Ne s'applique qu'aux minerais acceptes ; sinon on
        // laisse attemptTransformation() emettre le message "minerai non accepte" habituel.
        if (player.isSneaking() && manager.getTargetFamily(inHand.getType()) != null) {
            previewTransformation(player, machineBlock, inHand.getType());
            return;
        }

        attemptTransformation(player, machineBlock, inHand);
    }

    /** Apercu (clic-droit + sneak) : affiche la famille ciblee et la chance de reussite actuelle
     * (tier + bonus d'amelioration + streak en cours) sans rien consommer. */
    private void previewTransformation(Player player, Block machineBlock, Material ore) {
        String familyId = manager.getTargetFamily(ore);
        LuckyBlockFamily family = familyId != null ? luckyBlockManager.getFamily(familyId) : null;
        if (family == null) {
            messages.send(player, "machine.famille-introuvable");
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("caisse", family.displayName());
        placeholders.put("chance", String.valueOf((int) manager.getEffectiveChance(machineBlock)));
        placeholders.put("streak", String.valueOf(manager.getStreak(machineBlock)));
        messages.send(player, "machine.apercu", placeholders);
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
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 15, 0.4, 0.4, 0.4);
        player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
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
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40, 0.4, 0.5, 0.4, 0.1);
        loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 1f, 1.2f);
    }

    private void attemptTransformation(Player player, Block machineBlock, ItemStack inHand) {
        String familyId = manager.getTargetFamily(inHand.getType());
        if (familyId == null) {
            messages.send(player, "machine.minerai-non-accepte");
            return;
        }

        // Boost "carburant illimite" actif (voir machine-transformation.boost-carburant-illimite) :
        // ignore completement l'etat du carburant de CETTE machine pour ce joueur.
        boolean unlimitedFuel = manager.hasActiveFuelBoost(player.getUniqueId());

        if (!unlimitedFuel && manager.getFuel(machineBlock) <= 0) {
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

        // Echange 1 minerai contre 1 loot (plus de lot de 10) et relance le cooldown. Le carburant
        // est toujours consomme (sauf boost "carburant illimite" actif) puisque chaque echange
        // donne desormais TOUJOURS une recompense.
        Material ore = inHand.getType();
        int remaining = inHand.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining > 0 ? withAmount(inHand, remaining) : null);
        if (!unlimitedFuel) {
            manager.consumeCharge(machineBlock);
        }
        manager.markUsedNow(machineBlock);

        Location effectLocation = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        double chance = manager.getEffectiveChance(machineBlock);
        int pityThreshold = manager.getPityThreshold();
        boolean pityGuaranteed = pityThreshold > 0 && manager.getPityProgress(player.getUniqueId()) >= pityThreshold;
        boolean gotLuckyBlock = pityGuaranteed || ThreadLocalRandom.current().nextDouble(100.0) < chance;
        manager.recordAttempt(player.getUniqueId(), ore, gotLuckyBlock);
        manager.recordPityResult(player.getUniqueId(), gotLuckyBlock);

        boolean doubleLoot = manager.isDoubleLootActive();
        if (gotLuckyBlock) {
            manager.incrementStreak(machineBlock);

            ItemStack reward = luckyBlockManager.createItem(family);
            giveItem(player, reward);

            effectLocation.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLocation, 25, 0.4, 0.4, 0.4);
            player.playSound(effectLocation, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("caisse", family.displayName());
            messages.send(player, "machine.reussite", placeholders);

            questService.registerProgress(player, QuestType.MACHINE_TRANSFORM, family.id(), 1);

            // "Double loot" actif (voir /machine doubleloot) : ajoute aussi une recompense bonus.
            if (doubleLoot) {
                MachineLoot bonus = pickRandomLoot();
                giveItem(player, bonus.item());
                Map<String, String> bonusPlaceholders = new HashMap<>();
                bonusPlaceholders.put("item", bonus.displayName());
                messages.send(player, "machine.doubleloot-bonus-item", bonusPlaceholders);
            }
        } else {
            manager.resetStreak(machineBlock);

            MachineLoot loot = pickRandomLoot();
            giveItem(player, loot.item());
            effectLocation.getWorld().spawnParticle(Particle.END_ROD, effectLocation, 20, 0.4, 0.4, 0.4);
            player.playSound(effectLocation, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("item", loot.displayName());
            messages.send(player, "machine.reussite-item", placeholders);

            // "Double loot" actif : ajoute aussi le Lucky Block cible en bonus.
            if (doubleLoot) {
                giveItem(player, luckyBlockManager.createItem(family));
                Map<String, String> bonusPlaceholders = new HashMap<>();
                bonusPlaceholders.put("caisse", family.displayName());
                messages.send(player, "machine.doubleloot-bonus-luckyblock", bonusPlaceholders);
            }
        }
    }

    /** Une recompense "objet aleatoire" possible de la Machine : soit un item custom, soit un
     * generateur, soit l'une des 2 Machines elles-memes (voir pickRandomLoot). */
    private record MachineLoot(String displayName, ItemStack item) {
    }

    /** Un candidat au tirage pondere : son poids (voir MachineManager#getItemLootWeight et
     * consorts) et une fabrique paresseuse (n'est appelee QUE pour le candidat tire au sort, pour
     * ne jamais construire inutilement les ~60 ItemStack a chaque tentative). */
    private record LootCandidate(int weight, java.util.function.Supplier<MachineLoot> factory) {
    }

    /** Pioche au hasard, PONDERE par machine-transformation.poids-loot (voir custom_items.yml),
     * parmi : tous les items custom charges depuis custom_items.yml qui n'excluent pas ce tirage
     * (voir CustomItemDefinition#excluLootMachine), tous les types de Generateurs (voir
     * generateurs.yml), un Generateur de Lucky Block pour chaque famille ACTUELLEMENT active (voir
     * luckyblocks.yml), la Machine a Transformation elle-meme et la Machine a Miner (ces 3
     * dernieres categories uniquement si leurs managers sont deja injectes, voir
     * setGeneratorManager/setLuckyBlockGeneratorManager/setMiningMachineManager). Plus un poids
     * est eleve, plus l'entree a de chances de sortir. Ne renvoie jamais null : la Machine a
     * Transformation elle-meme est toujours candidate. */
    private MachineLoot pickRandomLoot() {
        List<LootCandidate> candidates = new ArrayList<>();

        for (CustomItemDefinition definition : customItemManager.getItemsSorted()) {
            if (definition.excluLootMachine()) {
                continue;
            }
            candidates.add(new LootCandidate(manager.getItemLootWeight(definition.id()),
                    () -> new MachineLoot(definition.displayName(), customItemManager.createItem(definition))));
        }

        if (generatorManager != null) {
            for (GeneratorManager.GeneratorType type : generatorManager.getTypes()) {
                candidates.add(new LootCandidate(manager.getGeneratorLootWeight(type.id()),
                        () -> new MachineLoot(type.displayName(), generatorManager.createGeneratorItem(type, 1))));
            }
        }

        if (luckyBlockGeneratorManager != null) {
            for (LuckyBlockFamily family : luckyBlockManager.getFamiliesSorted()) {
                candidates.add(new LootCandidate(manager.getGeneratorLuckyBlockLootWeight(family.id()), () -> {
                    ItemStack item = luckyBlockGeneratorManager.createItem(family, 1);
                    return new MachineLoot(item.getItemMeta().getDisplayName(), item);
                }));
            }
        }

        candidates.add(new LootCandidate(manager.getMachineLootWeight("transformation"), () -> {
            ItemStack item = manager.createMachineItem(1);
            return new MachineLoot(item.getItemMeta().getDisplayName(), item);
        }));

        if (miningMachineManager != null) {
            candidates.add(new LootCandidate(manager.getMachineLootWeight("miniere"), () -> {
                ItemStack item = miningMachineManager.createMachineItem(1);
                return new MachineLoot(item.getItemMeta().getDisplayName(), item);
            }));
        }

        int totalWeight = candidates.stream().mapToInt(LootCandidate::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (LootCandidate candidate : candidates) {
            cumulative += candidate.weight();
            if (roll < cumulative) {
                return candidate.factory().get();
            }
        }
        return candidates.get(candidates.size() - 1).factory().get();
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    /** Plafond de /machine simulate, meme raison que LuckyBlockService#MAX_SIMULATION. */
    private static final int MAX_SIMULATION = 200;

    /**
     * Outil admin (/machine simulate) : rejoue REELLEMENT count echanges pour ce minerai, sans
     * machine posee (utilise la chance du tier de base, sans bonus d'amelioration/streak), donne
     * VRAIMENT chaque recompense a l'admin (Lucky Block ou item custom), puis affiche un
     * recapitulatif. Ne consomme ni minerai ni carburant (il s'agit d'un test), mais interagit
     * avec le VRAI compteur de pity de l'admin comme un echange normal. Plafonne a MAX_SIMULATION.
     */
    public void simulate(Player admin, Material ore, int count) {
        String familyId = manager.getTargetFamily(ore);
        if (familyId == null) {
            messages.send(admin, "machine.minerai-non-accepte");
            return;
        }
        LuckyBlockFamily family = luckyBlockManager.getFamily(familyId);
        if (family == null) {
            messages.send(admin, "machine.famille-introuvable");
            return;
        }

        int total = Math.max(1, Math.min(count, MAX_SIMULATION));
        double chance = manager.getBaseTier().chance();
        int pityThreshold = manager.getPityThreshold();
        int luckyBlockCount = 0;
        Map<String, Integer> itemTally = new LinkedHashMap<>();

        for (int i = 0; i < total; i++) {
            boolean pityGuaranteed = pityThreshold > 0 && manager.getPityProgress(admin.getUniqueId()) >= pityThreshold;
            boolean gotLuckyBlock = pityGuaranteed || ThreadLocalRandom.current().nextDouble(100.0) < chance;
            manager.recordPityResult(admin.getUniqueId(), gotLuckyBlock);

            if (gotLuckyBlock) {
                luckyBlockCount++;
                giveItem(admin, luckyBlockManager.createItem(family));
            } else {
                MachineLoot loot = pickRandomLoot();
                giveItem(admin, loot.item());
                itemTally.merge(loot.displayName(), 1, Integer::sum);
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("nombre", String.valueOf(total));
        placeholders.put("minerai", ore.name());
        messages.send(admin, "machine.simulation-titre", placeholders);
        admin.sendMessage(MessageManager.color("&7- &6" + family.displayName() + " &7x&a" + luckyBlockCount
                + " &7(&e" + String.format("%.1f", 100.0 * luckyBlockCount / total) + "%&7)"));
        itemTally.forEach((name, obtained) -> admin.sendMessage(MessageManager.color(
                "&7- &e" + name + " &7x&a" + obtained + " &7(&e" + String.format("%.1f", 100.0 * obtained / total) + "%&7)")));
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
                world.spawnParticle(Particle.SMOKE, effectLocation, 1, 0.1, 0.1, 0.1, 0.0);
            } else if (fuel < 5) {
                world.spawnParticle(Particle.FLAME, effectLocation, 1, 0.15, 0.1, 0.15, 0.0);
            } else {
                world.spawnParticle(Particle.END_ROD, effectLocation, 2, 0.2, 0.15, 0.2, 0.0);
            }
            tickAutoFeed(block);
        }
        stale.forEach(manager::forgetMachine);
    }

    /**
     * Auto-alimentation : si un/des conteneur(s) sont colles a la machine, pioche automatiquement
     * 1 minerai accepte dans le conteneur d'entree des que le cooldown est ecoule (et qu'il reste
     * du carburant), et depose TOUJOURS une recompense (Lucky Block ou item custom aleatoire) dans
     * le conteneur de sortie. Purement automatique, aucun joueur n'est implique. Si aucun minerai
     * accepte n'est trouve dans l'entree, un petit indicateur (poudre rouge) apparait au-dessus
     * pour signaler qu'il faut la reapprovisionner.
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
        // 1 minerai = 1 loot : une seule unite suffit pour declencher un echange.
        Material match = null;
        int slot = -1;
        for (Material candidate : manager.getAcceptedMaterials()) {
            int candidateSlot = inputInventory.first(candidate);
            if (candidateSlot >= 0) {
                match = candidate;
                slot = candidateSlot;
                break;
            }
        }

        if (match == null) {
            // Aucun minerai accepte trouve dans l'entree : petit indicateur visuel de reapprovisionnement.
            Location warningLocation = containers.input().getLocation().add(0.5, 1.1, 0.5);
            containers.input().getWorld().spawnParticle(Particle.DUST, warningLocation, 6, 0.2, 0.1, 0.2, 0.0,
                    new Particle.DustOptions(Color.RED, 1.2f));
            return;
        }

        String familyId = manager.getTargetFamily(match);
        LuckyBlockFamily family = familyId != null ? luckyBlockManager.getFamily(familyId) : null;
        if (family == null) {
            return;
        }

        ItemStack stack = inputInventory.getItem(slot);
        int remainingInStack = stack.getAmount() - 1;
        if (remainingInStack > 0) {
            stack.setAmount(remainingInStack);
            inputInventory.setItem(slot, stack);
        } else {
            inputInventory.setItem(slot, null);
        }

        manager.consumeCharge(machineBlock);
        manager.markUsedNow(machineBlock);

        Location effectLocation = machineBlock.getLocation().add(0.5, 1.0, 0.5);
        boolean doubleLoot = manager.isDoubleLootActive();
        boolean gotLuckyBlock = ThreadLocalRandom.current().nextDouble(100.0) < manager.getEffectiveChance(machineBlock);
        if (gotLuckyBlock) {
            manager.incrementStreak(machineBlock);
            outputReward(outputInventory, containers, luckyBlockManager.createItem(family));
            playAutoFeedEffects(effectLocation, false);

            if (doubleLoot) {
                outputReward(outputInventory, containers, pickRandomLoot().item());
            }
        } else {
            manager.resetStreak(machineBlock);

            MachineLoot loot = pickRandomLoot();
            outputReward(outputInventory, containers, loot.item());
            playAutoFeedEffects(effectLocation, true);

            if (doubleLoot) {
                outputReward(outputInventory, containers, luckyBlockManager.createItem(family));
            }
        }
    }

    private void outputReward(Inventory outputInventory, MachineManager.AdjacentContainers containers, ItemStack reward) {
        Map<Integer, ItemStack> leftovers = outputInventory.addItem(reward);
        leftovers.values().forEach(leftover ->
                containers.output().getWorld().dropItemNaturally(containers.output().getLocation(), leftover));
    }

    /** Effet de particules a chaque echange auto-alimente reussi (toujours une recompense) :
     * villageois content pour un Lucky Block, END_ROD pour un item custom. */
    private void playAutoFeedEffects(Location location, boolean isCustomItem) {
        World world = location.getWorld();
        if (isCustomItem) {
            world.spawnParticle(Particle.END_ROD, location, 20, 0.4, 0.4, 0.4);
            world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
        } else {
            world.spawnParticle(Particle.HAPPY_VILLAGER, location, 25, 0.4, 0.4, 0.4);
            world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        }
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
