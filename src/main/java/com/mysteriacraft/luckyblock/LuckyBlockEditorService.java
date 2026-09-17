package com.mysteriacraft.luckyblock;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.luckyblock.gui.LuckyBlockFamiliesEditorGui;
import com.mysteriacraft.luckyblock.gui.LuckyBlockFamilyEditorGui;
import com.mysteriacraft.luckyblock.gui.LuckyBlockEffectsEditorGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Gere la saisie au clavier (via le chat) utilisee par l'editeur de familles/effets de Lucky
 * Block en jeu (voir LuckyBlockFamiliesEditorGui/LuckyBlockFamilyEditorGui/
 * LuckyBlockEffectsEditorGui et /luckyblockadmin editeur), sur le meme principe que
 * BattlePassEditorService/ShopEditorService.
 */
public class LuckyBlockEditorService {

    private record PendingFamilleField(String familyId, String champ) {
    }

    private record PendingEffetChance(String familyId, int index) {
    }

    private record PendingEffetCommande(String familyId) {
    }

    private static final Pattern DATE_MM_JJ = Pattern.compile("^(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$");

    private final Plugin plugin;
    private final LuckyBlockManager manager;
    private final MessageManager messages;

    private final Map<UUID, Boolean> pendingNomFamille = new ConcurrentHashMap<>();
    private final Map<UUID, PendingFamilleField> pendingFamilleField = new ConcurrentHashMap<>();
    private final Map<UUID, PendingEffetChance> pendingEffetChance = new ConcurrentHashMap<>();
    private final Map<UUID, PendingEffetCommande> pendingEffetCommande = new ConcurrentHashMap<>();

    public LuckyBlockEditorService(Plugin plugin, LuckyBlockManager manager, MessageManager messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    public void requestNomFamille(Player player) {
        pendingNomFamille.put(player.getUniqueId(), true);
        player.closeInventory();
        messages.send(player, "luckyblock.editeur-saisir-nom-famille");
    }

    public void requestFamilleField(Player player, String familyId, String champ) {
        pendingFamilleField.put(player.getUniqueId(), new PendingFamilleField(familyId, champ));
        player.closeInventory();
        messages.send(player, "luckyblock.editeur-saisir-" + champ);
    }

    public void requestEffetChance(Player player, String familyId, int index) {
        pendingEffetChance.put(player.getUniqueId(), new PendingEffetChance(familyId, index));
        player.closeInventory();
        messages.send(player, "luckyblock.editeur-saisir-effet-chance");
    }

    public void requestEffetCommande(Player player, String familyId) {
        pendingEffetCommande.put(player.getUniqueId(), new PendingEffetCommande(familyId));
        player.closeInventory();
        messages.send(player, "luckyblock.editeur-saisir-effet-commande");
    }

    public boolean hasPendingInput(UUID uuid) {
        return pendingNomFamille.containsKey(uuid) || pendingFamilleField.containsKey(uuid)
                || pendingEffetChance.containsKey(uuid) || pendingEffetCommande.containsKey(uuid);
    }

    public void handleChatInput(Player player, String message) {
        UUID uuid = player.getUniqueId();
        String valeur = message.trim();

        if (pendingNomFamille.remove(uuid) != null) {
            String id = valeur.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if (id.isEmpty()) {
                messages.send(player, "luckyblock.editeur-valeur-invalide");
                return;
            }
            ItemStack main = player.getInventory().getItemInMainHand();
            Material materiel = main != null && !main.getType().isAir() ? main.getType() : Material.GOLD_BLOCK;
            manager.addFamily(id, valeur, materiel);
            messages.send(player, "luckyblock.editeur-famille-creee");
            Bukkit.getScheduler().runTask(plugin, () -> new LuckyBlockFamiliesEditorGui(plugin, player, manager, this, messages).open());
            return;
        }

        PendingFamilleField pendingFamille = pendingFamilleField.remove(uuid);
        if (pendingFamille != null) {
            handleFamilleFieldInput(player, pendingFamille, valeur);
            return;
        }

        PendingEffetChance pendingEffet = pendingEffetChance.remove(uuid);
        if (pendingEffet != null) {
            try {
                double chance = Double.parseDouble(valeur);
                manager.setEffectChanceAt(pendingEffet.familyId(), pendingEffet.index(), chance);
                messages.send(player, "luckyblock.editeur-valeur-modifiee");
            } catch (NumberFormatException e) {
                messages.send(player, "luckyblock.editeur-valeur-invalide");
            }
            reopenEffectsEditor(player, pendingEffet.familyId());
            return;
        }

        PendingEffetCommande pendingCommande = pendingEffetCommande.remove(uuid);
        if (pendingCommande != null) {
            if (valeur.isEmpty()) {
                messages.send(player, "luckyblock.editeur-valeur-invalide");
            } else {
                manager.addGoodEffectFromCommand(pendingCommande.familyId(), valeur, 10.0);
                messages.send(player, "luckyblock.editeur-effet-commande-ajoute");
            }
            reopenEffectsEditor(player, pendingCommande.familyId());
        }
    }

    private void handleFamilleFieldInput(Player player, PendingFamilleField pending, String valeur) {
        if (pending.champ().equals("actif-du") || pending.champ().equals("actif-au")) {
            boolean effacer = valeur.equalsIgnoreCase("aucun") || valeur.equalsIgnoreCase("aucune");
            if (!effacer && !DATE_MM_JJ.matcher(valeur).matches()) {
                messages.send(player, "luckyblock.editeur-date-invalide");
                reopenFamilyEditor(player, pending.familyId());
                return;
            }
            String valeurFinale = effacer ? null : valeur;
            if (pending.champ().equals("actif-du")) {
                manager.setFamilleActifDu(pending.familyId(), valeurFinale);
            } else {
                manager.setFamilleActifAu(pending.familyId(), valeurFinale);
            }
            messages.send(player, "luckyblock.editeur-valeur-modifiee");
            reopenFamilyEditor(player, pending.familyId());
            return;
        }

        try {
            double nombre = Double.parseDouble(valeur);
            if (pending.champ().equals("prix-achat")) {
                manager.setFamillePrixAchat(pending.familyId(), nombre);
            } else if (pending.champ().equals("chance-bonne")) {
                manager.setFamilleChanceBonne(pending.familyId(), nombre);
            }
            messages.send(player, "luckyblock.editeur-valeur-modifiee");
        } catch (NumberFormatException e) {
            messages.send(player, "luckyblock.editeur-valeur-invalide");
        }
        reopenFamilyEditor(player, pending.familyId());
    }

    private void reopenFamilyEditor(Player player, String familyId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            LuckyBlockFamily family = manager.getFamily(familyId);
            if (family != null) {
                new LuckyBlockFamilyEditorGui(plugin, player, manager, this, family, messages).open();
            }
        });
    }

    private void reopenEffectsEditor(Player player, String familyId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            LuckyBlockFamily family = manager.getFamily(familyId);
            if (family != null) {
                new LuckyBlockEffectsEditorGui(plugin, player, manager, this, family, messages).open();
            }
        });
    }
}
