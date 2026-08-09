package io.github.langqi99.mekanicalcreate.content;

import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import mekanism.common.lib.math.voxel.VoxelCuboid;
import mekanism.common.lib.multiblock.CuboidStructureValidator;
import mekanism.common.lib.multiblock.FormationProtocol;
import mekanism.common.lib.multiblock.FormationProtocol.CasingType;
import mekanism.common.lib.multiblock.FormationProtocol.FormationResult;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Validates a variable 3-7 block Mek-style cuboid factory. */
public final class MekanicalFactoryValidator extends CuboidStructureValidator<MekanicalFactoryMultiblockData> {
    private static final VoxelCuboid MIN_SIZE = new VoxelCuboid(3, 3, 3);
    private static final VoxelCuboid MAX_SIZE = new VoxelCuboid(7, 7, 7);

    private boolean foundController;
    private boolean foundPort;
    private int speedCores;
    private int energyCores;
    private int fluidCores;
    private int catalystCores;

    public MekanicalFactoryValidator() {
        super(MIN_SIZE, MAX_SIZE);
    }

    @Override
    protected FormationResult validateFrame(FormationProtocol<MekanicalFactoryMultiblockData> context,
                                              BlockPos pos, BlockState state,
                                              CasingType type, boolean needsFrame) {
        BlockEntity tile = (BlockEntity) structure.getTile(pos);
        boolean controller = tile instanceof MekanicalFactoryControllerBlockEntity;
        if (foundController && controller) {
            return FormationResult.fail(Component.translatable(
                    "multiblock.mekanicalcreate.invalid.controller_conflict"), true);
        }
        foundController |= controller;
        foundPort |= tile instanceof MekanicalFactoryPortBlockEntity;
        return super.validateFrame(context, pos, state, type, needsFrame);
    }

    @Override
    protected boolean validateInner(BlockState state, Long2ObjectMap<ChunkAccess> chunkMap,
                                    BlockPos pos) {
        if (super.validateInner(state, chunkMap, pos)) {
            return true;
        }
        if (state.is(ModBlocks.MEKANICAL_FACTORY_SPEED_CORE.getBlock())) {
            speedCores++;
            return true;
        }
        if (state.is(ModBlocks.MEKANICAL_FACTORY_ENERGY_CORE.getBlock())) {
            energyCores++;
            return true;
        }
        if (state.is(ModBlocks.MEKANICAL_FACTORY_FLUID_CORE.getBlock())) {
            fluidCores++;
            return true;
        }
        if (state.is(ModBlocks.MEKANICAL_FACTORY_CATALYST_CORE.getBlock())) {
            catalystCores++;
            return true;
        }
        return false;
    }

    @Override
    protected CasingType getCasingType(BlockState state) {
        if (state.is(ModBlocks.MEKANICAL_FACTORY_CASING.getBlock())) {
            return CasingType.FRAME;
        }
        if (state.is(MekanismBlocks.STRUCTURAL_GLASS.getBlock())) {
            return CasingType.OTHER;
        }
        if (state.is(ModBlocks.MEKANICAL_FACTORY_PORT.getBlock())) {
            return CasingType.VALVE;
        }
        if (state.is(ModBlocks.FLUID_MEKANICAL_FACTORY.getBlock())) {
            return CasingType.OTHER;
        }
        return CasingType.INVALID;
    }

    @Override
    public FormationResult postcheck(MekanicalFactoryMultiblockData data,
                                     Long2ObjectMap<ChunkAccess> chunkMap) {
        if (!foundController) {
            return FormationResult.fail(Component.translatable(
                    "multiblock.mekanicalcreate.invalid.no_controller"));
        }
        if (!foundPort) {
            return FormationResult.fail(Component.translatable(
                    "multiblock.mekanicalcreate.invalid.no_port"));
        }
        // Core limits are effect caps, not formation limits. Extra cores remain
        // valid internal blocks and are simply ignored by setUpgradeCoreCounts.
        data.setUpgradeCoreCounts(speedCores, energyCores, fluidCores, catalystCores);
        return FormationResult.SUCCESS;
    }
}
