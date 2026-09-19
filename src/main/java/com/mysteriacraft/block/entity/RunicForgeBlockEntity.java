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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/** Reforge des lingots de fer en Lingots Runiques. */
public class RunicForgeBlockEntity extends AbstractMachineBlockEntity {

    private static final Map<Item, ItemStack> RECIPES = Map.of(
            Items.IRON_INGOT, new ItemStack(ModItems.RUNIC_INGOT.get())
    );

    public RunicForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RUNIC_FORGE.get(), pos, state,
                Component.translatable("block.mysteriacraft.runic_forge"),
                RECIPES, 140, 35, 12, 14000);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, ContainerData data) {
        return new MachineMenu(ModMenuTypes.RUNIC_FORGE_MENU.get(), containerId, playerInventory, this, data);
    }
}
