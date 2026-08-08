package io.github.langqi99.mekanicalcreate.content;

import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import mekanism.common.tile.interfaces.IBoundingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A full-size controller which reuses the proven simulation chamber internals.
 * The surrounding Mekanism bounding blocks proxy interaction and capabilities
 * back to this controller, just like Mekanism's own large generators.
 */
public final class SuperSimulationChamberBlockEntity extends SimulationChamberBlockEntity implements IBoundingBlock {
    public SuperSimulationChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SUPER_SIMULATION_CHAMBER, pos, state);
    }

    @Override
    public boolean isOffsetCapabilityDisabled(@NotNull BlockCapability<?, @Nullable Direction> capability,
                                              Direction side, @NotNull Vec3i offset) {
        return false;
    }
}
