package io.github.langqi99.mekanicalcreate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.ghostblock.GhostBlockParams;
import net.createmod.catnip.ghostblock.GhostBlockRenderer;
import net.createmod.catnip.ghostblock.GhostBlocks;
import net.createmod.catnip.placement.PlacementClient;
import net.createmod.catnip.placement.PlacementOffset;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ShaftPlacementPreview extends GhostBlockRenderer {
    private final BlockPos pos;
    private final BlockState state;

    private ShaftPlacementPreview(BlockPos pos, BlockState state) {
        this.pos = pos;
        this.state = state;
    }

    public static void show(Object key, PlacementOffset offset) {
        if (!offset.hasGhostState()) {
            return;
        }
        BlockState state = offset.getTransform().apply(offset.getGhostState());
        BlockPos pos = offset.getBlockPos();
        GhostBlocks.getInstance().showGhost(key, new ShaftPlacementPreview(pos, state),
                GhostBlockParams.of(state).at(pos), 1);
    }

    @Override
    public void render(PoseStack pose, SuperRenderTypeBuffer buffers, Vec3 camera, GhostBlockParams params) {
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        // The normal block model draws the shaft's bracket only. Use Create's kinetic model for the rod.
        CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, state)
                .color(255, 255, 255, Math.round(160 * PlacementClient.getCurrentAlpha()))
                .light(LightTexture.FULL_BRIGHT)
                .renderInto(pose, buffers.getEarlyBuffer(RenderType.translucent()));
        pose.popPose();
    }
}
