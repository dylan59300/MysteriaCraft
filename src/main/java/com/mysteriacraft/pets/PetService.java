package com.mysteriacraft.pets;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.pets.dressage.PetTrainingManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestre l'achat, l'invocation et le suivi des pets (un seul actif a la fois par joueur).
 * Ce sont de vrais mobs Bukkit : suivi implemente via Mob#getPathfinder() (API Paper), avec un
 * teleport de secours si le pet se retrouve trop loin ou change de monde. Limite connue : sans
 * retirer les selecteurs d'IA vanilla (hors de portee de l'API Bukkit standard), le pet garde
 * un comportement normal (peut s'asseoir, se reproduire, etc.) en plus de suivre son proprietaire.
 */
public class PetService implements RewardGiver.PetUnlockHandler {

    private static final double TELEPORT_DISTANCE = 20.0;
    private static final double FOLLOW_DISTANCE = 4.0;
    private static final long FOLLOW_PERIOD_TICKS = 10L;

    private final Plugin plugin;
    private final PetManager petManager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    private static final UUID DEGATS_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-4789-9abc-def012345678");

    private final Map<UUID, LivingEntity> activeEntities = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> followTasks = new ConcurrentHashMap<>();
    private final Map<UUID, PetDefinition> activeDefinitions = new ConcurrentHashMap<>();

    /** Branche apres coup (voir MysteriaCraft#onEnable) pour eviter une dependance circulaire :
     * le Dressage a besoin de PetService pour rafraichir les bonus, PetService a besoin du
     * Dressage pour connaitre le bonus supplementaire accorde par l'entrainement. */
    private PetTrainingManager trainingManager;

    public PetService(Plugin plugin, PetManager petManager, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    public void setTrainingManager(PetTrainingManager trainingManager) {
        this.trainingManager = trainingManager;
    }

    public void buy(Player player, String petId) {
        PetDefinition pet = petManager.getPet(petId);
        if (pet == null) {
            messages.send(player, "pets.introuvable");
            return;
        }
        if (!pet.isPurchasable()) {
            messages.send(player, "pets.non-achetable");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean alreadyUnlocked = petManager.isUnlocked(player.getUniqueId(), pet.id());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (alreadyUnlocked) {
                    messages.send(player, "pets.deja-debloque");
                    return;
                }
                if (!economyManager.has(player.getUniqueId(), pet.price()) || !economyManager.withdraw(player.getUniqueId(), pet.price())) {
                    messages.send(player, "pets.fonds-insuffisants");
                    return;
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    petManager.unlock(player.getUniqueId(), pet.id());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("pet", pet.displayName());
                        messages.send(player, "pets.achat-reussi", placeholders);
                    });
                });
            });
        });
    }

    /** Debloque un pet gratuitement (recompense de battlepass/quete). */
    @Override
    public void unlockFromReward(Player player, String petId) {
        PetDefinition pet = petManager.getPet(petId);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            petManager.unlock(player.getUniqueId(), petId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("pet", pet != null ? pet.displayName() : petId);
                messages.send(player, "pets.debloque-recompense", placeholders);
            });
        });
    }

    public void summon(Player player, String petId) {
        PetDefinition pet = petManager.getPet(petId);
        if (pet == null) {
            messages.send(player, "pets.introuvable");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean unlocked = petManager.isUnlocked(player.getUniqueId(), pet.id());
            if (!unlocked) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "pets.pas-debloque"));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                despawnActive(player);
                spawnPet(player, pet);
            });
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> petManager.setActivePetId(player.getUniqueId(), pet.id()));
        });
    }

    private void spawnPet(Player player, PetDefinition pet) {
        Location location = player.getLocation();
        Entity entity = location.getWorld().spawnEntity(location, pet.entityType());
        if (!(entity instanceof LivingEntity livingEntity)) {
            entity.remove();
            messages.send(player, "pets.introuvable");
            return;
        }

        livingEntity.setCustomName(MessageManager.color(pet.displayName()));
        livingEntity.setCustomNameVisible(true);
        livingEntity.setInvulnerable(true);
        livingEntity.setRemoveWhenFarAway(false);
        livingEntity.setPersistent(true);
        livingEntity.setCanPickupItems(false);

        if (livingEntity instanceof Tameable tameable) {
            tameable.setOwner(player);
            tameable.setTamed(true);
        }

        activeEntities.put(player.getUniqueId(), livingEntity);
        activeDefinitions.put(player.getUniqueId(), pet);
        startFollowTask(player, livingEntity);
        applyDegatsBonus(player, pet);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("pet", pet.displayName());
        messages.send(player, "pets.invoque", placeholders);
    }

    /** Applique le bonus de degats du pet actif au JOUEUR (attribut GENERIC_ATTACK_DAMAGE), plus
     * le bonus de Dressage accumule (voir PetTrainingManager), en remplacant tout modificateur
     * precedent (changement de pet ou nouveau niveau de dressage). */
    private void applyDegatsBonus(Player player, PetDefinition pet) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }
        attribute.getModifiers().stream()
                .filter(modifier -> modifier.getUniqueId().equals(DEGATS_MODIFIER_UUID))
                .forEach(attribute::removeModifier);
        double total = pet.degatsBonus() + (trainingManager != null ? trainingManager.getBonusDegats(player.getUniqueId()) : 0);
        if (total > 0) {
            attribute.addModifier(new AttributeModifier(DEGATS_MODIFIER_UUID, "mysteriacraft-pet-degats",
                    total, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void removeDegatsBonus(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }
        attribute.getModifiers().stream()
                .filter(modifier -> modifier.getUniqueId().equals(DEGATS_MODIFIER_UUID))
                .forEach(attribute::removeModifier);
    }

    /** Chance d'esquive (%) accordee par le pet actif de ce joueur (bonus de Dressage inclus), 0
     * si aucun pet actif. */
    public double getEsquivePourcent(UUID uuid) {
        PetDefinition pet = activeDefinitions.get(uuid);
        if (pet == null) {
            return 0;
        }
        double bonus = trainingManager != null ? trainingManager.getBonusEsquive(uuid) : 0;
        return pet.esquivePourcent() + bonus;
    }

    /** A appeler par le Dressage des qu'un joueur gagne un niveau, pour reappliquer immediatement
     * le bonus de degats mis a jour sur son pet actif (sans effet si aucun pet actif). */
    public void refreshTrainingBonus(Player player) {
        PetDefinition pet = activeDefinitions.get(player.getUniqueId());
        if (pet != null) {
            applyDegatsBonus(player, pet);
        }
    }

    private void startFollowTask(Player player, LivingEntity petEntity) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!petEntity.isValid() || petEntity.isDead()) {
                stopFollowTask(player.getUniqueId());
                return;
            }
            if (!player.isOnline()) {
                petEntity.remove();
                stopFollowTask(player.getUniqueId());
                return;
            }

            Location petLocation = petEntity.getLocation();
            Location ownerLocation = player.getLocation();

            if (!petLocation.getWorld().equals(ownerLocation.getWorld())
                    || petLocation.distance(ownerLocation) > TELEPORT_DISTANCE) {
                petEntity.teleport(ownerLocation);
                return;
            }

            if (petLocation.distance(ownerLocation) > FOLLOW_DISTANCE && petEntity instanceof Mob mob) {
                mob.getPathfinder().moveTo(ownerLocation, 1.0);
            }
        }, FOLLOW_PERIOD_TICKS, FOLLOW_PERIOD_TICKS);

        followTasks.put(player.getUniqueId(), task);
    }

    private void stopFollowTask(UUID uuid) {
        BukkitTask task = followTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        activeEntities.remove(uuid);
    }

    /** Retire le pet actif du joueur (entite + tache de suivi + bonus de combat), sans toucher
     * au deblocage en base. */
    public void despawnActive(Player player) {
        LivingEntity entity = activeEntities.remove(player.getUniqueId());
        if (entity != null) {
            entity.remove();
        }
        stopFollowTask(player.getUniqueId());
        activeDefinitions.remove(player.getUniqueId());
        removeDegatsBonus(player);
    }

    /** Desactive completement le pet actif (retire l'entite ET oublie le choix en base). */
    public void unsummon(Player player) {
        despawnActive(player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> petManager.setActivePetId(player.getUniqueId(), null));
        messages.send(player, "pets.range");
    }

    public boolean hasActivePet(UUID uuid) {
        return activeEntities.containsKey(uuid);
    }

    /** A appeler a la connexion : re-invoque automatiquement le pet actif enregistre en base. */
    public void respawnSavedPet(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String activePetId = petManager.getActivePetId(player.getUniqueId());
            if (activePetId == null) {
                return;
            }
            PetDefinition pet = petManager.getPet(activePetId);
            if (pet == null) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> spawnPet(player, pet));
        });
    }

    /** A appeler a la deconnexion : retire l'entite sans oublier le choix (elle reapparaitra a la reconnexion). */
    public void onPlayerQuit(Player player) {
        despawnActive(player);
    }
}
