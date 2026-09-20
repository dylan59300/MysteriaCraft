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

/** Condense l'Essence Magique en Poussière Arcanique. */
public class ArcaneCondenserBlockEntity extends AbstractMachineBlockEntity {

    private static final Map<Item, ItemStack> RECIPES = Map.of(
            ModItems.MAGIC_ESSENCE.get(), new ItemStack(ModItems.ARCANE_DUST.get(), 2)
    );

    public ArcaneCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_CONDENSER.get(), pos, state,
                Component.translatable("block.mysteriacraft.arcane_condenser"),
                RECIPES, 80, 25, 18, 9000);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, ContainerData data) {
        return new MachineMenu(ModMenuTypes.ARCANE_CONDENSER_MENU.get(), containerId, playerInventory, this, data);
    }
}
