package com.mysteriacraft.block;

import com.mysteriacraft.block.entity.AbstractMachineBlockEntity;
import com.mysteriacraft.block.entity.EssenceExtractorBlockEntity;
import com.mysteriacraft.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class EssenceExtractorBlock extends MachineBlock {

    public EssenceExtractorBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssenceExtractorBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ESSENCE_EXTRACTOR.get();
    }
}
