package com.mysteriacraft.block;

import com.mysteriacraft.block.entity.AbstractMachineBlockEntity;
import com.mysteriacraft.block.entity.ModBlockEntities;
import com.mysteriacraft.block.entity.RubyFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public class RubyFurnaceBlock extends MachineBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public RubyFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RubyFurnaceBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.RUBY_FURNACE.get();
    }
}
