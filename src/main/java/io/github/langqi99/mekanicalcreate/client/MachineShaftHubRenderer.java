package io.github.langqi99.mekanicalcreate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import io.github.langqi99.mekanicalcreate.content.KineticMachine;
import io.github.langqi99.mekanicalcreate.content.KineticMachineBlock;
import io.github.langqi99.mekanicalcreate.content.MachineShaft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MachineShaftHubRenderer<T extends BlockEntity & KineticMachine> implements BlockEntityRenderer<T> {
    private static final float SHAFT_LENGTH = 4 / 16f;

    public MachineShaftHubRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = machine.getBlockState();
        if (!(state.getBlock() instanceof KineticMachineBlock block)) {
            return;
        }
        Direction face = block.shaftFace(state);
        renderEnd(machine.shaft(), face, pose, buffers, light);
        if (block.hasOppositeShaft()) {
            renderEnd(machine.shaft(), face.getOpposite(), pose, buffers, light);
        }
    }

    private void renderEnd(MachineShaft shaft, Direction face, PoseStack pose, MultiBufferSource buffers, int light) {
        Direction.Axis axis = face.getAxis();
        // Center each end on the block face and leave two pixels of shaft visible outside the casing.
        float offset = (1 - SHAFT_LENGTH) / 2;
        pose.pushPose();
        pose.translate(axis == Direction.Axis.X ? offset + face.getStepX() * .5 : 0,
                0, axis == Direction.Axis.Z ? offset + face.getStepZ() * .5 : 0);
        pose.scale(axis == Direction.Axis.X ? SHAFT_LENGTH : 1, 1, axis == Direction.Axis.Z ? SHAFT_LENGTH : 1);
        KineticBlockEntityRenderer.renderRotatingKineticBlock(shaft, KineticBlockEntityRenderer.shaft(axis),
                pose, buffers.getBuffer(RenderType.solid()), light);
        pose.popPose();
    }
}
