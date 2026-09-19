package com.mysteriacraft.block.entity;

import com.mysteriacraft.block.RubyFurnaceBlock;
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

/** Raffine les Cristaux Mystiques en Rubis. Sa façade s'allume pendant la combustion. */
public class RubyFurnaceBlockEntity extends AbstractMachineBlockEntity {

    private static final Map<Item, ItemStack> RECIPES = Map.of(
            ModItems.MYSTIC_CRYSTAL.get(), new ItemStack(ModItems.RUBY.get())
    );

    public RubyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RUBY_FURNACE.get(), pos, state,
                Component.translatable("block.mysteriacraft.ruby_furnace"),
                RECIPES, 100, 25, 15, 11000);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, ContainerData data) {
        return new MachineMenu(ModMenuTypes.RUBY_FURNACE_MENU.get(), containerId, playerInventory, this, data);
    }

    @Override
    protected void onActiveChanged(boolean active) {
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(getBlockPos());
        if (state.hasProperty(RubyFurnaceBlock.LIT) && state.getValue(RubyFurnaceBlock.LIT) != active) {
            level.setBlock(getBlockPos(), state.setValue(RubyFurnaceBlock.LIT, active), 3);
        }
    }
}
