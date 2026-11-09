package io.github.langqi99.mekanicalcreate.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.kinetics.RotationPropagator;
import io.github.langqi99.mekanicalcreate.content.MachineShaft;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RotationPropagator.class)
abstract class RotationPropagatorMixin {
    @ModifyExpressionValue(
            method = {"findConnectedNeighbour", "handleRemoved", "propagateMissingSource"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"),
            require = 3)
    private static BlockEntity mekanicalcreate$findShaft(BlockEntity blockEntity) {
        return MachineShaft.resolve(blockEntity);
    }
}
