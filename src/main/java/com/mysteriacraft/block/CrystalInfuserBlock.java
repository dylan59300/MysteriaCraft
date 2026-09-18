package com.mysteriacraft.block;

import com.mysteriacraft.block.entity.AbstractMachineBlockEntity;
import com.mysteriacraft.block.entity.CrystalInfuserBlockEntity;
import com.mysteriacraft.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CrystalInfuserBlock extends MachineBlock {

    public CrystalInfuserBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrystalInfuserBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CRYSTAL_INFUSER.get();
    }
}
