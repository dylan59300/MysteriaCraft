package com.mysteriacraft.menu;

import com.mysteriacraft.block.entity.AbstractMachineBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.SlotItemHandler;

import java.util.Objects;

/**
 * Menu générique partagé par les 5 machines : un slot d'entrée, un slot de
 * sortie, l'inventaire du joueur, et les données synchronisées
 * (progression / énergie).
 */
public class MachineMenu extends AbstractContainerMenu {

    public final AbstractMachineBlockEntity blockEntity;
    private final ContainerLevelAccess levelAccess;
    private final ContainerData data;

    public MachineMenu(MenuType<MachineMenu> type, int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(type, containerId, playerInventory, getBlockEntity(playerInventory, extraData), new net.minecraft.world.inventory.SimpleContainerData(4));
    }

    public MachineMenu(MenuType<MachineMenu> type, int containerId, Inventory playerInventory,
                        AbstractMachineBlockEntity blockEntity, ContainerData data) {
        super(type, containerId);
        checkContainerDataCount(data, 4);
        this.blockEntity = blockEntity;
        this.data = data;
        this.levelAccess = blockEntity.getLevel() != null
                ? ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos())
                : ContainerLevelAccess.NULL;

        blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            this.addSlot(new SlotItemHandler(handler, AbstractMachineBlockEntity.INPUT_SLOT, 56, 35));
            this.addSlot(new SlotItemHandler(handler, AbstractMachineBlockEntity.OUTPUT_SLOT, 116, 35) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        });

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);

        addDataSlots(data);
    }

    private static AbstractMachineBlockEntity getBlockEntity(Inventory playerInventory, FriendlyByteBuf extraData) {
        Objects.requireNonNull(playerInventory, "playerInventory cannot be null");
        Objects.requireNonNull(extraData, "extraData cannot be null");
        BlockEntity entity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (entity instanceof AbstractMachineBlockEntity machineBlockEntity) {
            return machineBlockEntity;
        }
        throw new IllegalStateException("Bloc de machine introuvable à cette position : " + entity);
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        return data.get(1);
    }

    public int getEnergy() {
        return data.get(2);
    }

    public int getMaxEnergy() {
        return data.get(3);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copy = sourceStack.copy();

        final int machineSlots = 2;
        if (index < machineSlots) {
            if (!moveItemStackTo(sourceStack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(sourceStack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(levelAccess, player, blockEntity.getBlockState().getBlock());
    }
}
