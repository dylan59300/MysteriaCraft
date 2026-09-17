package com.mysteriacraft.admin;

import org.bukkit.Material;

import java.util.List;

/**
 * Liste statique de tous les modules geres par le panel admin unifie (/admin, voir AdminPanelGui).
 * Chaque module reutilise SA PROPRE commande existante (reload, et editeur si elle en a un) : le
 * panel ne fait qu'executer ces commandes a la place de l'admin, il ne duplique aucune logique.
 * Pensez a ajouter une entree ici pour tout nouveau module admin-gerable.
 */
public final class AdminRegistry {

    private AdminRegistry() {
    }

    public static final List<AdminModule> MODULES = List.of(
            new AdminModule("Economie", Material.GOLD_INGOT, "mysteriacraft.eco.admin", "ecoreload"),
            new AdminModule("Banque", Material.GOLD_BLOCK, "mysteriacraft.banque.admin", "banque reload"),
            new AdminModule("BattlePass", Material.NETHER_STAR, "mysteriacraft.battlepass.admin",
                    "battlepassadmin reload", "battlepassadmin editeur"),
            new AdminModule("Quetes", Material.WRITABLE_BOOK, "mysteriacraft.quests.admin", "questsadmin reload"),
            new AdminModule("Pets", Material.BONE, "mysteriacraft.pets.admin", "petsadmin reload"),
            new AdminModule("Lucky Block", Material.END_CRYSTAL, "mysteriacraft.luckyblock.admin", "luckyblockadmin reload"),
            new AdminModule("Items Custom", Material.NETHERITE_INGOT, "mysteriacraft.customitem.admin", "customitem reload"),
            new AdminModule("Machine a Transformation", Material.FURNACE, "mysteriacraft.machine.admin", "machine reload"),
            new AdminModule("Machine a Miner", Material.IRON_PICKAXE, "mysteriacraft.machineminiere.admin", "machineminiere reload"),
            new AdminModule("Generateurs d'Argent", Material.GOLD_NUGGET, "mysteriacraft.generateur.admin", "generateur reload"),
            new AdminModule("Generateurs Lucky Block", Material.DROPPER, "mysteriacraft.generateurlb.admin", "generateurlb reload"),
            new AdminModule("Guide", Material.BOOK, "mysteriacraft.guide.admin", "guide reload"),
            new AdminModule("Iles", Material.GRASS_BLOCK, "mysteriacraft.island.admin", "ile reload"),
            new AdminModule("PNJ Marchand", Material.EMERALD, "mysteriacraft.pnjmarchand.admin", "pnjmarchand reload"),
            new AdminModule("Table d'Enchantement", Material.ENCHANTING_TABLE, "mysteriacraft.enchantement.admin", "enchantementadmin reload"),
            new AdminModule("Classes", Material.DIAMOND_SWORD, "mysteriacraft.classes.admin", "classe reload"),
            new AdminModule("Hotel des Ventes", Material.ITEM_FRAME, "mysteriacraft.encheres.admin", "hoteldesventes reload"),
            new AdminModule("Boutique", Material.EMERALD_BLOCK, "mysteriacraft.boutique.admin",
                    "boutique reload", "boutique editeur"),
            new AdminModule("Kits", Material.CHEST, "mysteriacraft.kit.admin", "kit reload"),
            new AdminModule("Raffinerie", Material.BLAST_FURNACE, "mysteriacraft.raffinerie.admin", "raffinerie reload"),
            new AdminModule("Recyclage", Material.HOPPER, "mysteriacraft.recyclage.admin", "recycler reload"),
            new AdminModule("Gemmes", Material.EMERALD, "mysteriacraft.gemme.admin", "gemme reload"),
            new AdminModule("Runes", Material.ENDER_EYE, "mysteriacraft.rune.admin", "rune reload"),
            new AdminModule("Marche Noir", Material.BLACK_CONCRETE, "mysteriacraft.marchenoir.admin", "marchenoir reload"),
            new AdminModule("Talents", Material.EXPERIENCE_BOTTLE, "mysteriacraft.talents.admin", "talents reload"),
            new AdminModule("Metiers", Material.DIAMOND_PICKAXE, "mysteriacraft.metier.admin", "metier reload"),
            new AdminModule("Generateur Hybride", Material.MAGMA_BLOCK, "mysteriacraft.generateurhybride.admin", "generateurhybride reload"),
            new AdminModule("Mobs Custom", Material.ZOMBIE_HEAD, "mysteriacraft.mobcustom.admin", "mobcustom reload"),
            new AdminModule("Etabli Ameliore", Material.CRAFTING_TABLE, "mysteriacraft.etabli.admin", "etabli reload")
    );
}
