package com.mysteriacraft.pets.commands;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.pets.PetDefinition;
import com.mysteriacraft.pets.PetManager;
import com.mysteriacraft.pets.PetService;
import com.mysteriacraft.pets.gui.PetsGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;

public class PetsCommand implements CommandExecutor {

    private final Plugin plugin;
    private final PetManager petManager;
    private final PetService petService;
    private final MessageManager messages;

    public PetsCommand(Plugin plugin, PetManager petManager, PetService petService, MessageManager messages) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.petService = petService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<String> unlocked = new HashSet<>();
            for (PetDefinition pet : petManager.getPetsSorted()) {
                if (petManager.isUnlocked(player.getUniqueId(), pet.id())) {
                    unlocked.add(pet.id());
                }
            }
            String activePetId = petManager.getActivePetId(player.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () ->
                    new PetsGui(plugin, player, petManager, petService, messages, unlocked, activePetId).open());
        });
        return true;
    }
}
