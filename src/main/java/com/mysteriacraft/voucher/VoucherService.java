package com.mysteriacraft.voucher;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.reward.Reward;
import com.mysteriacraft.core.reward.RewardGiver;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fabrique/donne les objets Voucher et gere leur utilisation (confirmation + recompense). */
public class VoucherService {

    private final VoucherManager manager;
    private final RewardGiver rewardGiver;
    private final MessageManager messages;
    private final NamespacedKey voucherKey;

    public VoucherService(Plugin plugin, VoucherManager manager, RewardGiver rewardGiver, MessageManager messages) {
        this.manager = manager;
        this.rewardGiver = rewardGiver;
        this.messages = messages;
        this.voucherKey = new NamespacedKey(plugin, "voucher-id");
    }

    public ItemStack createItem(VoucherDefinition definition, int amount) {
        List<String> lore = new ArrayList<>(definition.lore());
        if (!lore.isEmpty()) {
            lore.add("");
        }
        lore.add(messages.raw("voucher.item-lore-utiliser"));
        ItemStack item = new ItemBuilder(definition.materiau(), amount)
                .name(definition.nom())
                .lore(lore)
                .build();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(voucherKey, PersistentDataType.STRING, definition.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Renvoie l'id du Voucher marque sur cet ItemStack, ou null si ce n'en est pas un. */
    public String getVoucherId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(voucherKey, PersistentDataType.STRING);
    }

    public void give(Player target, VoucherDefinition definition, int amount) {
        ItemStack item = createItem(definition, amount);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            messages.send(target, "voucher.inventaire-plein");
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantite", String.valueOf(amount));
        placeholders.put("nom", definition.nom());
        messages.send(target, "voucher.recu", placeholders);
    }

    /** Reconfirme que le joueur tient toujours ce Voucher en main principale (evite un exploit en
     * changeant d'item entre l'ouverture du menu de confirmation et le clic sur "Confirmer"),
     * consomme 1 exemplaire, puis donne la recompense (voir RewardType.COMMANDE). */
    public void redeem(Player player, String voucherId) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!voucherId.equals(getVoucherId(inHand))) {
            messages.send(player, "voucher.introuvable");
            return;
        }
        VoucherDefinition definition = manager.getVoucher(voucherId);
        if (definition == null) {
            messages.send(player, "voucher.introuvable");
            return;
        }
        int restant = inHand.getAmount() - 1;
        player.getInventory().setItemInMainHand(restant > 0 ? withAmount(inHand, restant) : null);

        if (definition.hasCommande()) {
            rewardGiver.give(player, Reward.ofCommand(definition.commande(), definition.nom(), null));
        }
        messages.send(player, "voucher.redeem-reussi");
    }

    private ItemStack withAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
