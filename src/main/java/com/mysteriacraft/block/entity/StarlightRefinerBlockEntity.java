package com.mysteriacraft.block.entity;

import com.mysteriacraft.item.ModItems;
import com.mysteriacraft.menu.MachineMenu;
import com.mysteriacraft.menu.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/** Raffine les Éclats d'Astrolumière en Lingots d'Astrolumière. */
public class StarlightRefinerBlockEntity extends AbstractMachineBlockEntity {

    private static final Map<Item, ItemStack> RECIPES = Map.of(
            ModItems.STARLIGHT_SHARD.get(), new ItemStack(ModItems.STARLIGHT_INGOT.get())
    );

    public StarlightRefinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STARLIGHT_REFINER.get(), pos, state,
                Component.translatable("block.mysteriacraft.starlight_refiner"),
                RECIPES, 160, 40, 20, 16000);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, ContainerData data) {
        return new MachineMenu(ModMenuTypes.STARLIGHT_REFINER_MENU.get(), containerId, playerInventory, this, data);
    }
}
