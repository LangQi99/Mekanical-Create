package io.github.langqi99.mekanicalcreate.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.langqi99.mekanicalcreate.content.MachineShaft;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({KineticBlockEntity.class, GeneratingKineticBlockEntity.class})
abstract class KineticSourceMixin {
    @ModifyExpressionValue(
            method = "setSource",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity mekanicalcreate$findSourceShaft(BlockEntity blockEntity) {
        return MachineShaft.resolve(blockEntity);
    }
}
