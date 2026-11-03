package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.content.kinetics.base.IRotate;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.UnaryOperator;

/**
 * Mekanism machine block that also accepts a Create shaft on its left and right
 * faces.
 *
 * <p>Mekanism machines cannot be {@code KineticBlockEntity} instances (Java
 * single inheritance), so the shaft cannot join the network through this block.
 * Instead the machine's block entity reads the adjacent shaft and adds its
 * stress demand to that shaft's network entry - see
 * {@code SimulationChamberBlockEntity#applyStressToShafts()}.</p>
 */
public class BlockMekanicalMachine<TILE extends TileEntityMekanism, TYPE extends BlockTypeTile<TILE>>
        extends BlockTile<TILE, TYPE> implements IRotate {

    public BlockMekanicalMachine(TYPE type, UnaryOperator<BlockBehaviour.Properties> properties) {
        super(type, properties);
    }

    public static Direction facingOf(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction side) {
        Direction facing = facingOf(state);
        return side == facing.getClockWise() || side == facing.getCounterClockWise();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        // Both allowed faces share one horizontal axis, which is what the shaft
        // renderer and the rotation transform need.
        return facingOf(state).getClockWise().getAxis();
    }
}
