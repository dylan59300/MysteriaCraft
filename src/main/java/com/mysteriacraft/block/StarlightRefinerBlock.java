package com.mysteriacraft.block;

import com.mysteriacraft.block.entity.AbstractMachineBlockEntity;
import com.mysteriacraft.block.entity.ModBlockEntities;
import com.mysteriacraft.block.entity.StarlightRefinerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class StarlightRefinerBlock extends MachineBlock {

    public StarlightRefinerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StarlightRefinerBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STARLIGHT_REFINER.get();
    }
}
