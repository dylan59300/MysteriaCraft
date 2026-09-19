package com.mysteriacraft.voucher;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.voucher.gui.VoucherAdminListGui;
import com.mysteriacraft.voucher.gui.VoucherEditorGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Gere la saisie au clavier (via le chat) utilisee par l'editeur de Vouchers en jeu (voir
 * VoucherAdminListGui/VoucherEditorGui et /voucher admin), sur le meme principe que ShopEditorService. */
public class VoucherEditorService {

    private record PendingVoucherField(String id, String champ) {
    }

    private final Plugin plugin;
    private final VoucherManager manager;
    private final MessageManager messages;

    private final Map<UUID, Material> pendingNewVoucherName = new ConcurrentHashMap<>();
    private final Map<UUID, PendingVoucherField> pendingVoucherField = new ConcurrentHashMap<>();

    public VoucherEditorService(Plugin plugin, VoucherManager manager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    /** Ferme le menu, retient l'icone (item tenu en main) et attend le nom du nouveau Voucher. */
    public void requestNewVoucher(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Material icone = held != null && !held.getType().isAir() ? held.getType() : Material.PAPER;
        pendingNewVoucherName.put(player.getUniqueId(), icone);
        player.closeInventory();
        messages.send(player, "voucher.editeur-saisir-nom");
    }

    public void requestVoucherField(Player player, String id, String champ) {
        pendingVoucherField.put(player.getUniqueId(), new PendingVoucherField(id, champ));
        player.closeInventory();
        messages.send(player, "voucher.editeur-saisir-" + champ);
    }

    public boolean hasPendingInput(UUID uuid) {
        return pendingNewVoucherName.containsKey(uuid) || pendingVoucherField.containsKey(uuid);
    }

    /** Appele par VoucherEditorChatListener (deja sur le thread principal) avec le message tape. */
    public void handleChatInput(Player player, String message) {
        UUID uuid = player.getUniqueId();

        Material pendingIcone = pendingNewVoucherName.remove(uuid);
        if (pendingIcone != null) {
            String nom = message.trim();
            String id = nom.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if (id.isEmpty()) {
                messages.send(player, "voucher.editeur-valeur-invalide");
                return;
            }
            String idFinal = id;
            int suffixe = 2;
            while (manager.exists(idFinal)) {
                idFinal = id + "_" + suffixe;
                suffixe++;
            }
            manager.addVoucher(idFinal, nom, pendingIcone);
            messages.send(player, "voucher.editeur-voucher-cree");
            String idOuvert = idFinal;
            Bukkit.getScheduler().runTask(plugin, () -> {
                VoucherDefinition definition = manager.getVoucher(idOuvert);
                if (definition != null) {
                    new VoucherEditorGui(player, manager, this, definition, messages).open();
                }
            });
            return;
        }

        PendingVoucherField pending = pendingVoucherField.remove(uuid);
        if (pending == null) {
            return;
        }

        String valeur = message.trim();
        switch (pending.champ()) {
            case "nom" -> manager.setVoucherNom(pending.id(), valeur);
            case "commande" -> manager.setVoucherCommande(pending.id(), valeur);
            case "materiau" -> {
                Material materiau = Material.matchMaterial(valeur.toUpperCase());
                if (materiau == null) {
                    messages.send(player, "voucher.editeur-materiau-invalide");
                    reopenEditor(player, pending.id());
                    return;
                }
                manager.setVoucherMateriau(pending.id(), materiau);
            }
            case "lore" -> {
                boolean vide = valeur.isEmpty() || valeur.equalsIgnoreCase("aucun") || valeur.equalsIgnoreCase("aucune");
                manager.setVoucherLore(pending.id(), vide ? List.of() : List.of(valeur.split(";")));
            }
            default -> {
            }
        }
        messages.send(player, "voucher.editeur-valeur-modifiee");
        reopenEditor(player, pending.id());
    }

    private void reopenEditor(Player player, String id) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            VoucherDefinition definition = manager.getVoucher(id);
            if (definition != null) {
                new VoucherEditorGui(player, manager, this, definition, messages).open();
            } else {
                new VoucherAdminListGui(player, manager, this, messages).open();
            }
        });
    }
}
