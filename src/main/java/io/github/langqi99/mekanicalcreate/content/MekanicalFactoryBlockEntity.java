package io.github.langqi99.mekanicalcreate.content;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import mekanism.common.lib.multiblock.MultiblockManager;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** Common external node for the fixed Mekanical Factory multiblock. */
public class MekanicalFactoryBlockEntity extends TileEntityMultiblock<MekanicalFactoryMultiblockData> {
    protected MekanicalFactoryBlockEntity(Holder<Block> blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
    }

    @NotNull
    @Override
    public MekanicalFactoryMultiblockData createMultiblock() {
        return new MekanicalFactoryMultiblockData(this);
    }

    @Override
    public MultiblockManager<MekanicalFactoryMultiblockData> getManager() {
        return MekanicalCreate.MEKANICAL_FACTORY_MANAGER;
    }

    @Override
    public boolean canBeMaster() {
        return false;
    }
}
