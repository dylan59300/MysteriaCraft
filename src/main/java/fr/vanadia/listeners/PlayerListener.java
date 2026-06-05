package fr.vanadia.listeners;

import fr.vanadia.Vanadia;
import fr.vanadia.player.PlayerData;
import fr.vanadia.quests.Quest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final Vanadia plugin;

    public PlayerListener(Vanadia plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerManager().loadOrCreate(player);

        if (data.getClassType() != null) {
            plugin.getClassManager().applyClassStats(player, data);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getDungeonManager().isInDungeon(player.getUniqueId())) {
            plugin.getDungeonManager().leaveDungeon(player);
        }

        plugin.getPlayerManager().unloadPlayer(player.getUniqueId());
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Material material = event.getItem().getItemStack().getType();
        int amount = event.getItem().getItemStack().getAmount();

        plugin.getQuestManager().updateProgress(player,
                Quest.QuestType.COLLECT_ITEM, material.name(), amount);
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Material result = event.getRecipe().getResult().getType();
        int amount = event.getRecipe().getResult().getAmount();

        plugin.getQuestManager().updateProgress(player,
                Quest.QuestType.CRAFT, result.name(), amount);
    }
}
