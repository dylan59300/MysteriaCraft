package com.mysteriacraft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Hache de Jade : casse les blocs sur une zone 3x3 autour du bloc miné. */
public class JadeAxeItem extends AxeItem {

    public JadeAxeItem(Tier tier, float attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entityLiving) {
        boolean result = super.mineBlock(stack, level, state, pos, entityLiving);

        if (level.isClientSide || !(entityLiving instanceof Player player) || player.isCreative()) {
            return result;
        }

        Direction face = Direction.getNearest(
                entityLiving.getLookAngle().x, entityLiving.getLookAngle().y, entityLiving.getLookAngle().z);

        for (BlockPos extraPos : neighbors3x3(pos, face)) {
            if (extraPos.equals(pos)) {
                continue;
            }
            BlockState extraState = level.getBlockState(extraPos);
            if (!extraState.isAir() && extraState.getDestroySpeed(level, extraPos) >= 0) {
                level.destroyBlock(extraPos, true, player);
            }
        }

        return result;
    }

    private static List<BlockPos> neighbors3x3(BlockPos center, Direction face) {
        List<BlockPos> positions = new ArrayList<>(9);
        Direction.Axis axis = face.getAxis();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                BlockPos pos = switch (axis) {
                    case X -> center.offset(0, a, b);
                    case Y -> center.offset(a, 0, b);
                    case Z -> center.offset(a, b, 0);
                };
                positions.add(pos);
            }
        }
        return positions;
    }
}
