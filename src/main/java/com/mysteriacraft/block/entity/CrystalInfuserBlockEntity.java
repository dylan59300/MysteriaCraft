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

/** Infuse les Cristaux Mystiques d'énergie arcanique pour en faire des Cristaux Infusés. */
public class CrystalInfuserBlockEntity extends AbstractMachineBlockEntity {

    private static final Map<Item, ItemStack> RECIPES = Map.of(
            ModItems.MYSTIC_CRYSTAL.get(), new ItemStack(ModItems.INFUSED_CRYSTAL.get())
    );

    public CrystalInfuserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL_INFUSER.get(), pos, state,
                Component.translatable("block.mysteriacraft.crystal_infuser"),
                RECIPES, 120, 30, 15, 12000);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, ContainerData data) {
        return new MachineMenu(ModMenuTypes.CRYSTAL_INFUSER_MENU.get(), containerId, playerInventory, this, data);
    }
}
