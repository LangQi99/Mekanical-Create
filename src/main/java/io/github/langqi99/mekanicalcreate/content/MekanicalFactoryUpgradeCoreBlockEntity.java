package io.github.langqi99.mekanicalcreate.content;

import mekanism.common.tile.prefab.TileEntityInternalMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Internal marker block used by the validator to derive factory upgrades. */
public final class MekanicalFactoryUpgradeCoreBlockEntity extends TileEntityInternalMultiblock {
    public MekanicalFactoryUpgradeCoreBlockEntity(Holder<Block> blockProvider,
                                                   BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
    }
}
