package com.mysteriacraft.customitems;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.reward.RewardGiver;
import com.mysteriacraft.economy.EconomyManager;
import org.bukkit.EntityEffect;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gere le tirage des drops de minerais custom a la casse d'un bloc source, ainsi que la
 * revente d'objets custom contre la monnaie interne (/customitem sell).
 */
public class CustomItemService implements RewardGiver.CustomItemGiveHandler {

    private final CustomItemManager manager;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public CustomItemService(CustomItemManager manager, EconomyManager economyManager, MessageManager messages) {
        this.manager = manager;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    /** Appele par le listener a la casse d'un bloc : tire chaque item custom eligible independamment. */
    public void handleOreBreak(BlockBreakEvent event) {
        List<CustomItemDefinition> candidates = manager.getItemsForOre(event.getBlock().getType());
        if (candidates.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();

        for (CustomItemDefinition definition : candidates) {
            if (ThreadLocalRandom.current().nextDouble(100.0) >= definition.dropChance()) {
                continue;
            }
            ItemStack item = manager.createItem(definition);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("item", definition.displayName());
            messages.send(player, "customitem.trouve", placeholders);
        }
    }

    /**
     * Enchantement custom "Vol de vie" (vol-de-vie dans custom_items.yml) + durabilite custom des
     * armes full-custom : appele sur chaque coup porte par un joueur tenant un item custom.
     * Le vol de vie soigne l'attaquant d'un % des degats infliges (plafonne a sa vie max) ; la
     * durabilite custom decremente d'un coup et brise l'arme (avec message/son) a 0.
     */
    public void handleMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String customItemId = manager.getCustomItemId(inHand);
        if (customItemId == null) {
            return;
        }
        CustomItemDefinition definition = manager.getItem(customItemId);
        if (definition == null) {
            return;
        }

        if (definition.volDeVie() > 0) {
            double heal = event.getFinalDamage() * (definition.volDeVie() / 100.0);
            AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
            player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
        }

        applyDurabilityUse(player, inHand, definition);
    }

    /** Durabilite custom des outils full-custom : appele sur chaque bloc casse avec un tel outil en main. */
    public void handleToolDurability(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String customItemId = manager.getCustomItemId(inHand);
        if (customItemId == null) {
            return;
        }
        CustomItemDefinition definition = manager.getItem(customItemId);
        if (definition == null) {
            return;
        }
        applyDurabilityUse(player, inHand, definition);
    }

    /** Decremente d'une utilisation la durabilite custom de cet item EN MAIN PRINCIPALE, et le
     * brise (retire de l'inventaire, avec effet/message) une fois a 0. Ne fait rien si durabilite
     * custom desactivee sur cette definition. */
    private void applyDurabilityUse(Player player, ItemStack item, CustomItemDefinition definition) {
        if (!definition.hasCustomDurability()) {
            return;
        }
        int remaining = manager.getRemainingDurability(item);
        if (remaining < 0) {
            remaining = definition.durabiliteCustom();
        }
        remaining--;

        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            player.playEffect(EntityEffect.HURT);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("item", definition.displayName());
            messages.send(player, "customitem.durabilite-cassee", placeholders);
            return;
        }
        manager.setRemainingDurability(item, definition, remaining);
    }

    /** Repare a 100% l'item custom en main (durabilite custom uniquement), appele en tenant le
     * "kit_reparation" (voir custom_items.yml) dans l'autre main. Consomme 1 kit. */
    public void repairItem(Player player, ItemStack itemToRepair, ItemStack repairKit) {
        String customItemId = manager.getCustomItemId(itemToRepair);
        CustomItemDefinition definition = customItemId != null ? manager.getItem(customItemId) : null;
        if (definition == null || !definition.hasCustomDurability()) {
            messages.send(player, "customitem.reparation-invalide");
            return;
        }
        if (manager.getRemainingDurability(itemToRepair) >= definition.durabiliteCustom()) {
            messages.send(player, "customitem.reparation-inutile");
            return;
        }

        manager.setRemainingDurability(itemToRepair, definition, definition.durabiliteCustom());

        int remainingKits = repairKit.getAmount() - 1;
        player.getInventory().setItemInOffHand(remainingKits > 0 ? withAmount(repairKit, remainingKits) : null);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", definition.displayName());
        messages.send(player, "customitem.reparation-reussie", placeholders);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.4f);
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    /** Donne un item custom gratuitement (recompense de battlepass/quete/luckyblock). */
    @Override
    public void giveCustomItem(Player player, String customItemId, int amount) {
        CustomItemDefinition definition = manager.getItem(customItemId);
        if (definition == null) {
            return;
        }
        ItemStack item = manager.createItem(definition, amount);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    /** Vend tous les exemplaires d'un item custom presents dans l'inventaire du joueur. */
    public void sellAll(Player player, String itemId) {
        CustomItemDefinition definition = manager.getItem(itemId);
        if (definition == null) {
            messages.send(player, "customitem.introuvable");
            return;
        }
        if (!definition.isSellable()) {
            messages.send(player, "customitem.non-vendable");
            return;
        }

        ItemStack[] contents = player.getInventory().getContents();
        int totalFound = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && definition.id().equals(manager.getCustomItemId(stack))) {
                totalFound += stack.getAmount();
                player.getInventory().setItem(i, null);
            }
        }

        if (totalFound == 0) {
            messages.send(player, "customitem.aucun-en-inventaire");
            return;
        }

        double total = definition.sellPrice() * totalFound;
        economyManager.deposit(player.getUniqueId(), total);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(totalFound));
        placeholders.put("item", definition.displayName());
        placeholders.put("total", economyManager.format(total));
        messages.send(player, "customitem.vente-reussie", placeholders);
    }
}
