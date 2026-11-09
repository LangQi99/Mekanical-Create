package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public interface KineticMachineBlock extends IRotate {
    Direction shaftFace(BlockState state);

    default boolean hasOppositeShaft() {
        return true;
    }

    @Override
    default Direction.Axis getRotationAxis(BlockState state) {
        return shaftFace(state).getAxis();
    }

    @Override
    default boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction side) {
        Direction face = shaftFace(state);
        return side == face || hasOppositeShaft() && side == face.getOpposite();
    }
}
