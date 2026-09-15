package com.mysteriacraft.pets.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.pets.PetManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PetsAdminCommand implements CommandExecutor {

    private final ConfigManager petsConfig;
    private final PetManager petManager;
    private final MessageManager messages;

    public PetsAdminCommand(ConfigManager petsConfig, PetManager petManager, MessageManager messages) {
        this.petsConfig = petsConfig;
        this.petManager = petManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mysteriacraft.pets.admin")) {
            messages.send(sender, "general.pas-de-permission");
            return true;
        }
        petsConfig.reload();
        petManager.loadPets();
        messages.send(sender, "pets.reload");
        return true;
    }
}
