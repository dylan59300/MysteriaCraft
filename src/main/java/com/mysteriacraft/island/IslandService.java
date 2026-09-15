package com.mysteriacraft.island;

import com.mysteriacraft.battlepass.BattlePassService;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.customitems.CustomItemDefinition;
import com.mysteriacraft.customitems.CustomItemManager;
import com.mysteriacraft.customitems.generator.GeneratorManager;
import com.mysteriacraft.customitems.machine.MachineManager;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestre les actions du module Iles : creation (plateforme + kit + teleportation),
 * teleportation (dont visite d'une autre ile), invitations (en attente, en memoire),
 * agrandissement (paye via l'economie), et distribution des recompenses de palier ET de defis
 * (via RewardGiver + BattlePassService, comme les autres modules).
 */
public class IslandService {

    private final IslandManager manager;
    private final EconomyManager economyManager;
    private final RewardGiver rewardGiver;
    private final BattlePassService battlePassService;
    private final CustomItemManager customItemManager;
    private final MachineManager machineManager;
    private final GeneratorManager generatorManager;
    private final MessageManager messages;

    /** Invitations en attente : proprietaire -> ensemble des UUID invites (en memoire uniquement,
     * une invitation ne survit pas a un redemarrage, ce qui est acceptable pour une action ponctuelle). */
    private final Map<UUID, Set<UUID>> pendingInvites = new ConcurrentHashMap<>();

    public IslandService(IslandManager manager, EconomyManager economyManager, RewardGiver rewardGiver,
                          BattlePassService battlePassService, CustomItemManager customItemManager,
                          MachineManager machineManager, GeneratorManager generatorManager, MessageManager messages) {
        this.manager = manager;
        this.economyManager = economyManager;
        this.rewardGiver = rewardGiver;
        this.battlePassService = battlePassService;
        this.customItemManager = customItemManager;
        this.machineManager = machineManager;
        this.generatorManager = generatorManager;
        this.messages = messages;
    }

    /** Cree l'ile du joueur (si aucune deja), construit sa plateforme de depart (+ annexe avec
     * Machine a Transformation et Generateur de Fer), donne le kit et l'argent de depart, et le
     * teleporte dessus. */
    public void create(Player player) {
        if (manager.hasIsland(player.getUniqueId())) {
            messages.send(player, "ile.deja-existante");
            return;
        }
        IslandManager.Island island = manager.createIsland(player.getUniqueId());
        buildStarterPlatform(island);
        buildAnnex(island);
        giveStarterKit(player);
        double startingMoney = manager.getStartingMoney();
        if (startingMoney > 0) {
            economyManager.deposit(player.getUniqueId(), startingMoney);
        }
        teleportToIsland(player, island);
        messages.send(player, "ile.creee");
        messages.send(player, "ile.creee-guide");
    }

    /**
     * Plateforme de depart : 5x5 (herbe sur terre sur pierre), un arbre decale du centre, une
     * petite mare avec canne a sucre et une parcelle de pastèques, un carre de sable/gravier, un
     * mouton, et une petite poche minee sous la plateforme avec quelques minerais.
     */
    private void buildStarterPlatform(IslandManager.Island island) {
        World world = manager.getWorld();
        int cx = island.centerX;
        int cy = island.centerY;
        int cz = island.centerZ;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(cx + dx, cy - 1, cz + dz).setType(Material.STONE);
                world.getBlockAt(cx + dx, cy, cz + dz).setType(Material.DIRT);
                world.getBlockAt(cx + dx, cy + 1, cz + dz).setType(Material.GRASS_BLOCK);
            }
        }

        // Arbre decale en (+1, +1) pour laisser le centre (point d'atterrissage) degage.
        int tx = cx + 1;
        int tz = cz + 1;
        for (int h = 0; h < 4; h++) {
            world.getBlockAt(tx, cy + 2 + h, tz).setType(Material.OAK_LOG);
        }
        int leafY = cy + 5;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setIfAir(world, tx + dx, leafY, tz + dz, Material.OAK_LEAVES);
                setIfAir(world, tx + dx, leafY - 1, tz + dz, Material.OAK_LEAVES);
            }
        }
        world.getBlockAt(tx, leafY + 1, tz).setType(Material.OAK_LEAVES);

        // Coffre de secours (vide, purement decoratif/utilitaire) a cote du point d'atterrissage.
        world.getBlockAt(cx - 2, cy + 2, cz - 2).setType(Material.CHEST);

        // Petite mare (1x2) avec canne a sucre sur la berge, dans le coin oppose de l'arbre.
        int wx = cx - 1;
        int wz = cz - 2;
        world.getBlockAt(wx, cy + 1, wz).setType(Material.WATER);
        world.getBlockAt(wx, cy + 1, wz - 1).setType(Material.WATER);
        world.getBlockAt(wx - 1, cy + 2, wz).setType(Material.SUGAR_CANE);
        world.getBlockAt(wx + 1, cy + 2, wz - 1).setType(Material.SUGAR_CANE);

        // Parcelle de pastèques (terre agricole + tige murie) pres de la mare.
        int mx = cx - 2;
        int mz = cz + 1;
        world.getBlockAt(mx, cy, mz).setType(Material.FARMLAND);
        world.getBlockAt(mx, cy + 1, mz).setType(Material.MELON_STEM);
        world.getBlockAt(mx - 1, cy + 1, mz).setType(Material.MELON);

        // Carre de sable/gravier (acces direct au verre sans avoir a en trouver).
        world.getBlockAt(cx + 2, cy + 1, cz - 1).setType(Material.SAND);
        world.getBlockAt(cx + 2, cy + 1, cz - 2).setType(Material.GRAVEL);

        // Petite poche minee sous la plateforme, avec quelques minerais visibles.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, cy - 3, cz + dz).setType(Material.CAVE_AIR);
                world.getBlockAt(cx + dx, cy - 4, cz + dz).setType(Material.STONE);
            }
        }
        world.getBlockAt(cx, cy - 4, cz).setType(Material.COAL_ORE);
        world.getBlockAt(cx - 1, cy - 4, cz + 1).setType(Material.IRON_ORE);

        // Mouton (laine + reproduction).
        world.spawnEntity(new Location(world, cx - 1.5, cy + 2, cz + 1.5), EntityType.SHEEP);
    }

    /**
     * Annexe reliee a la plateforme principale par un pont de terre : une Machine a
     * Transformation et un Generateur de Fer, deja prets a l'emploi (kit "les machines a leur
     * disposition des le depart").
     */
    private void buildAnnex(IslandManager.Island island) {
        World world = manager.getWorld();
        int cx = island.centerX;
        int cy = island.centerY;
        int cz = island.centerZ;
        int ax = cx - 4;
        int az = cz - 1;

        // Pont de terre reliant la plateforme principale a l'annexe.
        for (int x = cx - 3; x >= ax; x--) {
            world.getBlockAt(x, cy, az).setType(Material.DIRT);
        }
        // Petite plateforme 3x3 pour l'annexe.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(ax + dx, cy - 1, az + dz).setType(Material.STONE);
                world.getBlockAt(ax + dx, cy, az + dz).setType(Material.DIRT);
            }
        }

        Block machineBlock = world.getBlockAt(ax - 1, cy + 1, az);
        machineManager.tagBlock(machineBlock);

        GeneratorManager.GeneratorType ironType = generatorManager.getType("fer");
        if (ironType != null) {
            Block generatorBlock = world.getBlockAt(ax + 1, cy + 1, az);
            generatorManager.tagBlock(generatorBlock, ironType, island.owner());
        }
    }

    private void setIfAir(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() == Material.AIR) {
            block.setType(material);
        }
    }

    private void giveStarterKit(Player player) {
        for (Map<?, ?> raw : manager.getStarterKitRaw()) {
            int amount = raw.containsKey("quantite") ? Integer.parseInt(String.valueOf(raw.get("quantite"))) : 1;
            ItemStack item;
            if (raw.containsKey("objet-custom")) {
                CustomItemDefinition definition = customItemManager.getItem(String.valueOf(raw.get("objet-custom")));
                if (definition == null) {
                    continue;
                }
                item = customItemManager.createItem(definition, Math.max(1, amount));
            } else {
                Material material = Material.matchMaterial(String.valueOf(raw.get("materiel")));
                if (material == null) {
                    continue;
                }
                item = new ItemStack(material, Math.max(1, amount));
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    /** Teleporte au point d'atterrissage de cette ile : le home personnalise (/ile sethome) si
     * defini, sinon le centre par defaut. */
    public void teleportToIsland(Player player, IslandManager.Island island) {
        player.teleport(manager.getHomeLocation(island));
    }

    public void teleportToWorldSpawn(Player player) {
        player.teleport(manager.getWorldSpawn());
    }

    /** /ile sethome : definit le point d'atterrissage de SA PROPRE ile a la position actuelle du
     * joueur, a condition qu'il s'y trouve reellement (dans les limites protegees). */
    public void setHome(Player player) {
        IslandManager.Island island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            messages.send(player, "ile.aucune");
            return;
        }
        Location location = player.getLocation();
        if (!location.getWorld().equals(manager.getWorld())
                || manager.getIslandAt(location.getBlockX(), location.getBlockZ()) != island) {
            messages.send(player, "ile.sethome-hors-limites");
            return;
        }
        manager.setHome(island, location);
        messages.send(player, "ile.sethome-defini");
    }

    /** /ile visit <joueur> : teleporte en LECTURE SEULE sur l'ile d'un autre joueur (la
     * protection empeche deja toute construction pour un non-membre). */
    public void visit(Player visitor, Player target) {
        IslandManager.Island island = manager.getIsland(target.getUniqueId());
        if (island == null) {
            messages.send(visitor, "ile.aucune-pour-joueur");
            return;
        }
        visitor.teleport(manager.getHomeLocation(island));
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("joueur", target.getName());
        messages.send(visitor, "ile.visite", placeholders);
    }

    /** Supprime l'ile du joueur (sans confirmation supplementaire : geree en amont par la commande). */
    public void delete(Player player) {
        if (!manager.hasIsland(player.getUniqueId())) {
            messages.send(player, "ile.aucune");
            return;
        }
        manager.deleteIsland(player.getUniqueId());
        teleportToWorldSpawn(player);
        messages.send(player, "ile.supprimee");
    }

    // ---- Invitations ----

    public void invite(Player owner, Player target) {
        IslandManager.Island island = manager.getIsland(owner.getUniqueId());
        if (island == null) {
            messages.send(owner, "ile.aucune");
            return;
        }
        if (target.getUniqueId().equals(owner.getUniqueId())) {
            messages.send(owner, "ile.invitation-soi-meme");
            return;
        }
        if (island.isTrusted(target.getUniqueId())) {
            messages.send(owner, "ile.deja-membre");
            return;
        }
        pendingInvites.computeIfAbsent(owner.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(target.getUniqueId());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("joueur", target.getName());
        messages.send(owner, "ile.invitation-envoyee", placeholders);

        Map<String, String> targetPlaceholders = new HashMap<>();
        targetPlaceholders.put("joueur", owner.getName());
        messages.send(target, "ile.invitation-recue", targetPlaceholders);
    }

    public void acceptInvite(Player target, Player owner) {
        Set<UUID> invites = pendingInvites.get(owner.getUniqueId());
        if (invites == null || !invites.remove(target.getUniqueId())) {
            messages.send(target, "ile.invitation-introuvable");
            return;
        }
        IslandManager.Island island = manager.getIsland(owner.getUniqueId());
        if (island == null) {
            messages.send(target, "ile.invitation-introuvable");
            return;
        }
        manager.addMember(island, target.getUniqueId());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("joueur", owner.getName());
        messages.send(target, "ile.invitation-acceptee", placeholders);

        Map<String, String> ownerPlaceholders = new HashMap<>();
        ownerPlaceholders.put("joueur", target.getName());
        messages.send(owner, "ile.membre-rejoint", ownerPlaceholders);

        giveChallengeRewards(owner, manager.checkChallenges(island, IslandManager.ChallengeType.MEMBRES, island.members().size()));
    }

    public boolean kickMember(Player owner, UUID member) {
        IslandManager.Island island = manager.getIsland(owner.getUniqueId());
        if (island == null) {
            return false;
        }
        return manager.removeMember(island, member);
    }

    // ---- Agrandissement ----

    public void upgrade(Player player) {
        IslandManager.Island island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            messages.send(player, "ile.aucune");
            return;
        }
        if (manager.isMaxSize(island)) {
            messages.send(player, "ile.taille-max");
            return;
        }
        double price = manager.getUpgradePrice();
        if (!economyManager.has(player.getUniqueId(), price) || !economyManager.withdraw(player.getUniqueId(), price)) {
            messages.send(player, "ile.fonds-insuffisants");
            return;
        }
        int newSize = manager.upgrade(island);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("taille", String.valueOf(newSize));
        placeholders.put("prix", economyManager.format(price));
        messages.send(player, "ile.agrandie", placeholders);

        giveChallengeRewards(player, manager.checkChallenges(island, IslandManager.ChallengeType.TAILLE, newSize));
    }

    // ---- Valeur / paliers (appele par IslandProtectionListener a chaque pose/casse) ----

    public void onBlockPlaced(IslandManager.Island island, Material material) {
        manager.incrementBlocksPlaced(island);
        applyValueChange(island, manager.getBlockValue(material));
        Player owner = Bukkit.getPlayer(island.owner());
        if (owner != null) {
            giveChallengeRewards(owner, manager.checkChallenges(island, IslandManager.ChallengeType.BLOCS_POSES, island.blocksPlaced()));
        }
    }

    public void onBlockBroken(IslandManager.Island island, Material material) {
        applyValueChange(island, -manager.getBlockValue(material));
    }

    private void applyValueChange(IslandManager.Island island, int delta) {
        if (delta == 0) {
            return;
        }
        List<Reward> newTiers = manager.addValueAndCheckTiers(island, delta);
        Player owner = Bukkit.getPlayer(island.owner());

        if (!newTiers.isEmpty() && owner != null) {
            for (Reward reward : newTiers) {
                rewardGiver.give(owner, reward);
            }
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("niveau", String.valueOf(island.value()));
            messages.send(owner, "ile.palier-atteint", placeholders);
        }

        if (owner != null) {
            giveChallengeRewards(owner, manager.checkChallenges(island, IslandManager.ChallengeType.NIVEAU, island.value()));
        }
    }

    /**
     * Affiche une jauge de particules le long du perimetre de l'ile sur laquelle se trouve
     * CHAQUE joueur actuellement present dans le monde des Iles (appele periodiquement depuis
     * MysteriaCraft, voir tickAmbientParticles des autres modules). Echantillonne le perimetre
     * tous les 2 blocs pour rester leger malgre une grande ile.
     */
    public void tickBorders() {
        World islandWorld = manager.getWorld();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(islandWorld)) {
                continue;
            }
            IslandManager.Island island = manager.getIslandAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            if (island == null) {
                continue;
            }
            drawBorder(island, player);
        }
    }

    private void drawBorder(IslandManager.Island island, Player player) {
        World world = manager.getWorld();
        int cx = island.centerX;
        int cz = island.centerZ;
        int size = island.size();
        double y = player.getLocation().getY();
        int step = 2;

        for (int dx = -size; dx <= size; dx += step) {
            spawnBorderParticle(world, player, cx + dx, y, cz - size);
            spawnBorderParticle(world, player, cx + dx, y, cz + size);
        }
        for (int dz = -size; dz <= size; dz += step) {
            spawnBorderParticle(world, player, cx - size, y, cz + dz);
            spawnBorderParticle(world, player, cx + size, y, cz + dz);
        }
    }

    private void spawnBorderParticle(World world, Player player, int x, double y, int z) {
        player.spawnParticle(org.bukkit.Particle.END_ROD, x + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
    }

    /** Donne l'xp BattlePass + la recompense optionnelle de chaque defi nouvellement complete, et
     * envoie un message par defi. */
    private void giveChallengeRewards(Player player, List<IslandManager.Challenge> completed) {
        for (IslandManager.Challenge challenge : completed) {
            if (challenge.xp() > 0) {
                battlePassService.addXp(player, challenge.xp());
            }
            if (challenge.reward() != null) {
                rewardGiver.give(player, challenge.reward());
            }
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("defi", challenge.id());
            placeholders.put("xp", String.valueOf(challenge.xp()));
            messages.send(player, "ile.defi-complete", placeholders);
        }
    }
}
