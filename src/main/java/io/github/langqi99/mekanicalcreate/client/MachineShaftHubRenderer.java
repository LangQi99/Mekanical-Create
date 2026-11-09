package io.github.langqi99.mekanicalcreate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import io.github.langqi99.mekanicalcreate.content.KineticMachine;
import io.github.langqi99.mekanicalcreate.content.KineticMachineBlock;
import io.github.langqi99.mekanicalcreate.content.MachineShaft;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MachineShaftHubRenderer<T extends BlockEntity & KineticMachine> implements BlockEntityRenderer<T> {
    public MachineShaftHubRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = machine.getBlockState();
        if (!(state.getBlock() instanceof KineticMachineBlock block)) {
            return;
        }
        Direction face = block.shaftFace(state);
        renderEnd(machine.shaft(), state, face, pose, buffers, light);
        if (block.hasOppositeShaft()) {
            renderEnd(machine.shaft(), state, face.getOpposite(), pose, buffers, light);
        }
    }

    private void renderEnd(MachineShaft shaft, BlockState state, Direction face, PoseStack pose,
                           MultiBufferSource buffers, int light) {
        var vertices = buffers.getBuffer(RenderType.solid());
        CachedBuffers.partialFacing(ClientRenderers.SHAFT_BEARING, state, face).light(light).renderInto(pose, vertices);

        // A connected half shaft stops at the block boundary. Show the cap just past
        // the casing when the socket is open, so it remains visible at rest.
        pose.pushPose();
        Level level = shaft.getLevel();
        if (level != null && level.getBlockState(shaft.getBlockPos().relative(face)).canBeReplaced()) {
            double capOffset = 1 / 32.0;
            pose.translate(face.getStepX() * capOffset, 0, face.getStepZ() * capOffset);
        }
        KineticBlockEntityRenderer.renderRotatingBuffer(shaft,
                CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, face), pose, vertices, light);
        pose.popPose();
    }
}
