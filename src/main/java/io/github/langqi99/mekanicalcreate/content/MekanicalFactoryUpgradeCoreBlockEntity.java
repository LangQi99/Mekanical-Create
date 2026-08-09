package io.github.langqi99.mekanicalcreate.content;

import mekanism.common.tile.prefab.TileEntityInternalMultiblock;
import mekanism.api.providers.IBlockProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Internal marker block used by the validator to derive factory upgrades. */
public final class MekanicalFactoryUpgradeCoreBlockEntity extends TileEntityInternalMultiblock {
    public MekanicalFactoryUpgradeCoreBlockEntity(IBlockProvider blockProvider,
                                                   BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
    }
}
