package com.mysteriacraft.block.entity;

import com.mysteriacraft.block.ModBlocks;
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

/** Extrait l'Essence Magique à partir de bûches luminescentes. */
public class EssenceExtractorBlockEntity extends AbstractMachineBlockEntity {

    private static final Map<Item, ItemStack> RECIPES = Map.of(
            Item.byBlock(ModBlocks.LUMINESCENT_LOG.get()), new ItemStack(ModItems.MAGIC_ESSENCE.get())
    );

    public EssenceExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENCE_EXTRACTOR.get(), pos, state,
                Component.translatable("block.mysteriacraft.essence_extractor"),
                RECIPES, 100, 20, 15, 10000);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, ContainerData data) {
        return new MachineMenu(ModMenuTypes.ESSENCE_EXTRACTOR_MENU.get(), containerId, playerInventory, this, data);
    }
}
