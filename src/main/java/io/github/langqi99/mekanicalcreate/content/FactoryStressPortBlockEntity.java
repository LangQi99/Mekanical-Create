package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.langqi99.mekanicalcreate.registry.ModStress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Existing stress interface blocks act as shafts with a network readout. */
public final class FactoryStressPortBlockEntity extends KineticBlockEntity {
    public FactoryStressPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModStress.stressPortType(), pos, state);
    }

    public long networkStress() {
        return hasNetwork() ? (long) stress : 0L;
    }

    public long networkCapacity() {
        return hasNetwork() ? (long) capacity : 0L;
    }
}
