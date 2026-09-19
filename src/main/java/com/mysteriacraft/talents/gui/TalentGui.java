package com.mysteriacraft.talents.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.talents.TalentManager;
import com.mysteriacraft.talents.TalentService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu listant les noeuds de talents avec leur statut (instantane calcule a l'ouverture, voir
 * TalentCommand) : debloque, disponible (prerequis rempli + assez de points), ou verrouille.
 * Cliquer un noeud disponible tente reellement de le debloquer cote serveur.
 */
public class TalentGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] NODE_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final TalentManager manager;
    private final TalentService service;
    private final MessageManager messages;
    private final int points;
    private final List<String> unlockedIds;
    private final Map<Integer, String> slotToNodeId = new HashMap<>();

    private Inventory inventory;

    public TalentGui(Player viewer, TalentManager manager, TalentService service, MessageManager messages,
                      int points, List<String> unlockedIds) {
        super(viewer);
        this.manager = manager;
        this.service = service;
        this.messages = messages;
        this.points = points;
        this.unlockedIds = unlockedIds;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("talents.titre-gui")));
        holder.setInventory(inventory);
        render();
        return inventory;
    }

    private void render() {
        slotToNodeId.clear();
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        List<TalentManager.TalentNode> nodes = manager.getNodesSorted();
        for (int i = 0; i < NODE_SLOTS.length && i < nodes.size(); i++) {
            TalentManager.TalentNode node = nodes.get(i);
            inventory.setItem(NODE_SLOTS[i], buildIcon(node));
            slotToNodeId.put(NODE_SLOTS[i], node.id());
        }

        inventory.setItem(22, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name(messages.raw("talents.gui-points"))
                .lore(List.of(String.valueOf(points)))
                .build());
    }

    private ItemStack buildIcon(TalentManager.TalentNode node) {
        boolean unlocked = unlockedIds.contains(node.id());
        boolean prerequisOk = node.prerequis() == null || unlockedIds.contains(node.prerequis());
        boolean disponible = !unlocked && prerequisOk && points >= node.coutPoints();

        ItemStack icon = node.icon() != null ? node.icon().clone() : new ItemStack(Material.PAPER);
        List<String> lore = new ArrayList<>();
        lore.add(MessageManager.color("&7" + node.description()));
        lore.add("");
        if (unlocked) {
            lore.add(messages.raw("talents.gui-debloque"));
        } else if (!prerequisOk) {
            TalentManager.TalentNode prereq = manager.getNode(node.prerequis());
            lore.add(messages.raw("talents.gui-prerequis").replace("{prerequis}", prereq != null ? prereq.nom() : node.prerequis()));
        } else {
            lore.add(messages.raw("talents.gui-cout").replace("{cout}", String.valueOf(node.coutPoints())));
            lore.add(disponible ? messages.raw("talents.gui-clic-debloquer") : messages.raw("talents.gui-points-insuffisants"));
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        String nodeId = slotToNodeId.get(event.getSlot());
        if (nodeId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        service.unlock(player, nodeId);
    }
}
