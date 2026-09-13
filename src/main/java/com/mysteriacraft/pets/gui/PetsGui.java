package com.mysteriacraft.pets.gui;

import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.core.gui.ItemBuilder;
import com.mysteriacraft.core.gui.Menu;
import com.mysteriacraft.core.gui.MenuHolder;
import com.mysteriacraft.pets.PetDefinition;
import com.mysteriacraft.pets.PetManager;
import com.mysteriacraft.pets.PetService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Menu listant les pets : achat s'ils sont achetables et pas encore debloques, ou activation/
 * desactivation (un seul pet actif a la fois) s'ils le sont deja.
 */
public class PetsGui extends Menu {

    private static final int SIZE = 27;
    private static final int[] PET_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PREVIOUS_SLOT = 18;
    private static final int NEXT_SLOT = 26;

    private final Plugin plugin;
    private final PetManager petManager;
    private final PetService petService;
    private final MessageManager messages;
    private final Set<String> unlockedPets;
    private final String activePetId;

    private Inventory inventory;
    private List<PetDefinition> pets;
    private int page = 0;
    private final Map<Integer, String> slotToPetId = new HashMap<>();

    public PetsGui(Plugin plugin, Player viewer, PetManager petManager, PetService petService,
                   MessageManager messages, Set<String> unlockedPets, String activePetId) {
        super(viewer);
        this.plugin = plugin;
        this.petManager = petManager;
        this.petService = petService;
        this.messages = messages;
        this.unlockedPets = unlockedPets;
        this.activePetId = activePetId;
    }

    @Override
    public Inventory build() {
        MenuHolder holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, SIZE, MessageManager.color(messages.raw("pets.titre-gui")));
        holder.setInventory(inventory);
        this.pets = new ArrayList<>(petManager.getPetsSorted());
        render();
        return inventory;
    }

    private int maxPage() {
        return Math.max(1, (int) Math.ceil(pets.size() / (double) PET_SLOTS.length));
    }

    private void render() {
        slotToPetId.clear();

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        int firstIndex = page * PET_SLOTS.length;
        for (int i = 0; i < PET_SLOTS.length; i++) {
            int index = firstIndex + i;
            if (index >= pets.size()) {
                break;
            }
            PetDefinition pet = pets.get(index);
            inventory.setItem(PET_SLOTS[i], buildPetItem(pet));
            slotToPetId.put(PET_SLOTS[i], pet.id());
        }

        int maxPage = maxPage();
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW)
                    .name(messages.raw("kits.gui-page-precedente")).build());
        }
        if (page < maxPage - 1) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW)
                    .name(messages.raw("kits.gui-page-suivante")).build());
        }
    }

    private ItemStack buildPetItem(PetDefinition pet) {
        boolean unlocked = unlockedPets.contains(pet.id());
        boolean active = pet.id().equalsIgnoreCase(activePetId);

        List<String> lore = new ArrayList<>(pet.lore());
        lore.add("");
        if (active) {
            lore.add(messages.raw("pets.gui-actif"));
            lore.add(messages.raw("pets.gui-clic-desactiver"));
        } else if (unlocked) {
            lore.add(messages.raw("pets.gui-debloque"));
            lore.add(messages.raw("pets.gui-clic-activer"));
        } else if (pet.isPurchasable()) {
            lore.add(replace(messages.raw("pets.gui-prix"), "prix", String.valueOf((long) pet.price()) + "$"));
            lore.add(messages.raw("pets.gui-clic-acheter"));
        } else {
            lore.add(messages.raw("pets.gui-recompense-uniquement"));
        }

        String name = active ? "&a&l" + stripColor(pet.displayName()) : pet.displayName();
        Material icon = unlocked ? pet.icon() : (pet.isPurchasable() ? pet.icon() : Material.GRAY_DYE);

        return new ItemBuilder(icon).name(name).lore(lore).build();
    }

    private String stripColor(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "");
    }

    private String replace(String text, String key, String value) {
        return text.replace("{" + key + "}", value);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == NEXT_SLOT && page < maxPage() - 1) {
            page++;
            render();
            return;
        }

        String petId = slotToPetId.get(slot);
        if (petId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean unlocked = unlockedPets.contains(petId);
        boolean active = petId.equalsIgnoreCase(activePetId);

        player.closeInventory();
        if (active) {
            petService.unsummon(player);
        } else if (unlocked) {
            petService.summon(player, petId);
        } else {
            petService.buy(player, petId);
        }
    }
}
