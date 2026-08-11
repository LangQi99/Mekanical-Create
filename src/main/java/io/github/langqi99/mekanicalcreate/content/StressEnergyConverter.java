package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Converts Create's configured stress impact into factory FE/t. */
final class StressEnergyConverter {
    static final int VIRTUAL_RPM = 64;
    static final int STRESS_UNITS_PER_FE = 4;
    static final long MINIMUM_ENERGY_PER_TICK = 16L;

    private StressEnergyConverter() {
    }

    static long energyPerTick(ItemStack module, int recipeComplexity) {
        double impact = stressImpact(module);
        if (module.is(AllBlocks.CRUSHING_WHEEL.asItem())) {
            // A working crusher always uses two powered wheels.
            impact *= 2;
        } else if (module.is(AllBlocks.MECHANICAL_CRAFTER.asItem())) {
            // Each occupied crafting position represents one powered crafter.
            impact *= Math.max(1, recipeComplexity);
        }
        if (!Double.isFinite(impact) || impact <= 0) {
            return MINIMUM_ENERGY_PER_TICK;
        }
        return Math.max(MINIMUM_ENERGY_PER_TICK,
                (long) Math.ceil(impact * VIRTUAL_RPM / STRESS_UNITS_PER_FE));
    }

    static double stressImpact(ItemStack module) {
        if (!(module.getItem() instanceof BlockItem blockItem)) {
            return 0;
        }
        Block block = blockItem.getBlock();
        double configuredImpact = BlockStressValues.getImpact(block);
        if (Double.isFinite(configuredImpact) && configuredImpact > 0) {
            return configuredImpact;
        }
        if (block instanceof EntityBlock entityBlock) {
            try {
                BlockEntity blockEntity = entityBlock.newBlockEntity(
                        BlockPos.ZERO, block.defaultBlockState());
                if (blockEntity instanceof KineticBlockEntity kineticBlockEntity) {
                    double calculatedImpact = kineticBlockEntity.calculateStressApplied();
                    if (Double.isFinite(calculatedImpact) && calculatedImpact > 0) {
                        return calculatedImpact;
                    }
                }
            } catch (LinkageError | RuntimeException exception) {
                MekanicalCreate.LOGGER.debug(
                        "Could not calculate addon stress impact for {}",
                        blockItem.getBlock().getDescriptionId(), exception);
            }
        }
        return 0;
    }
}
