package com.mysteriacraft.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Base commune à toutes les machines MysteriaCraft : un slot d'entrée, un
 * slot de sortie, une réserve d'énergie magique ambiante qui se régénère
 * seule, et une recette simple item -> item définie par chaque machine.
 */
public abstract class AbstractMachineBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    private final Map<Item, ItemStack> recipes;
    private final int maxProgress;
    private final int energyPerTick;
    private final int energyRegenPerTick;
    private final Component title;

    private int progress = 0;
    protected final EnergyStorage energyStorage;

    protected final ItemStackHandler itemHandler = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == OUTPUT_SLOT) {
                return false;
            }
            return recipes.containsKey(stack.getItem());
        }
    };

    private final LazyOptional<IItemHandler> itemHandlerOptional = LazyOptional.of(() -> itemHandler);
    private final LazyOptional<IEnergyStorage> energyOptional = LazyOptional.of(() -> energyStorage);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> energyStorage.getEnergyStored();
                case 3 -> energyStorage.getMaxEnergyStored();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = value;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    protected AbstractMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                          Component title, Map<Item, ItemStack> recipes,
                                          int maxProgress, int energyPerTick, int energyRegenPerTick,
                                          int energyCapacity) {
        super(type, pos, state);
        this.title = title;
        this.recipes = recipes;
        this.maxProgress = maxProgress;
        this.energyPerTick = energyPerTick;
        this.energyRegenPerTick = energyRegenPerTick;
        this.energyStorage = new EnergyStorage(energyCapacity);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AbstractMachineBlockEntity blockEntity) {
        if (level.isClientSide) {
            return;
        }

        blockEntity.energyStorage.receiveEnergy(blockEntity.energyRegenPerTick, false);

        ItemStack input = blockEntity.itemHandler.getStackInSlot(INPUT_SLOT);
        ItemStack recipeOutput = blockEntity.recipes.get(input.getItem());
        boolean hasRecipe = !input.isEmpty() && recipeOutput != null && blockEntity.canInsertOutput(recipeOutput);
        boolean hasEnergy = blockEntity.energyStorage.getEnergyStored() >= blockEntity.energyPerTick;

        boolean changed = false;
        if (hasRecipe && hasEnergy) {
            blockEntity.energyStorage.extractEnergy(blockEntity.energyPerTick, false);
            blockEntity.progress++;
            changed = true;
            if (blockEntity.progress >= blockEntity.maxProgress) {
                blockEntity.craftItem(recipeOutput);
                blockEntity.progress = 0;
            }
        } else if (blockEntity.progress != 0) {
            blockEntity.progress = 0;
            changed = true;
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    private boolean canInsertOutput(ItemStack recipeOutput) {
        ItemStack current = itemHandler.getStackInSlot(OUTPUT_SLOT);
        if (current.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameTags(current, recipeOutput)
                && current.getCount() + recipeOutput.getCount() <= current.getMaxStackSize();
    }

    private void craftItem(ItemStack recipeOutput) {
        itemHandler.extractItem(INPUT_SLOT, 1, false);
        ItemStack current = itemHandler.getStackInSlot(OUTPUT_SLOT);
        if (current.isEmpty()) {
            itemHandler.setStackInSlot(OUTPUT_SLOT, recipeOutput.copy());
        } else {
            current.grow(recipeOutput.getCount());
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        SimpleContainer container = new SimpleContainer(itemHandler.getSlots());
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            container.setItem(i, itemHandler.getStackInSlot(i));
        }
        Containers.dropContents(level, pos, container);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerOptional.cast();
        }
        if (cap == ForgeCapabilities.ENERGY) {
            return energyOptional.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerOptional.invalidate();
        energyOptional.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.put("inventory", itemHandler.serializeNBT());
        tag.putInt("progress", progress);
        tag.putInt("energy", energyStorage.getEnergyStored());
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        itemHandler.deserializeNBT(tag.getCompound("inventory"));
        progress = tag.getInt("progress");
        energyStorage.receiveEnergy(tag.getInt("energy") - energyStorage.getEnergyStored(), false);
    }

    @Override
    public Component getDisplayName() {
        return title;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return createMenu(containerId, playerInventory, data);
    }

    protected abstract AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, ContainerData data);

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }
}
