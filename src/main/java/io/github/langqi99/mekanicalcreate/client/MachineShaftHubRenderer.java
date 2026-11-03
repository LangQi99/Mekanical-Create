package io.github.langqi99.mekanicalcreate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Draws the Create shaft hub on the left and right faces of a machine.
 *
 * <p>Registered for every Mekanical Create machine block entity, so the hub
 * appears regardless of whether the block is the single-block machine or the
 * multiblock controller/casing. Both ends share one angle: one shaft runs
 * through the machine, so a shaft on either side drives both ends.</p>
 */
public final class MachineShaftHubRenderer implements BlockEntityRenderer<BlockEntity> {
    private static final BlockState SHAFT = AllBlocks.SHAFT.getDefaultState()
            .setValue(ShaftBlock.AXIS, Direction.Axis.Y);
    private static final int FULL_LIGHT = 0xF000F0;

    public MachineShaftHubRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BlockEntity blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = blockEntity.getLevel();
        BlockState state = blockEntity.getBlockState();
        if (level == null || !state.hasProperty(BlockStateProperties.FACING)) {
            return;
        }
        Direction facing = state.getValue(BlockStateProperties.FACING);
        if (facing.getAxis().isVertical()) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        Direction[] sides = {facing.getClockWise(), facing.getCounterClockWise()};
        float angle = 0.0F;
        for (Direction probe : sides) {
            if (level.getBlockEntity(pos.relative(probe)) instanceof KineticBlockEntity driving) {
                angle = KineticBlockEntityRenderer.getAngleForBe(
                        driving, driving.getBlockPos(), probe.getAxis());
                break;
            }
        }
        for (Direction side : sides) {
            pose.pushPose();
            // 轴心推到方块表面之外，避免被机器自身的不透明模型遮住
            pose.translate(0.5 + side.getStepX() * 1.02,
                    0.5 + side.getStepY() * 1.02,
                    0.5 + side.getStepZ() * 1.02);
            switch (side.getAxis()) {
                case X -> pose.mulPose(Axis.YP.rotationDegrees(90.0F));
                case Z -> pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                default -> {
                }
            }
            pose.mulPose(Axis.YP.rotationDegrees(-angle));
            pose.scale(0.62F, 0.62F, 0.62F);
            pose.translate(-0.5, -0.5, -0.5);
            Minecraft.getInstance().getBlockRenderer()
                    .renderSingleBlock(SHAFT, pose, buffers, FULL_LIGHT, overlay);
            pose.popPose();
        }
    }
}
