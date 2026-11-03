package io.github.langqi99.mekanicalcreate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import io.github.langqi99.mekanicalcreate.content.FactoryStressPortBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * 和 {@code appliedcreate:kinetic_energy_acceptor} 完全相同的做法：
 * 继承 {@code KineticBlockEntityRenderer}，在 {@code renderSafe} 里用
 * {@code shaft(axis)} + {@code getRotatedModel} + {@code renderRotatingBuffer} 自己画那根轴。
 *
 * <p>关键点：对方不是注册 Create 的 {@code ShaftRenderer}，而是自己继承渲染器并把轴画出来；
 * 之前我直接注册 {@code ShaftRenderer}，几何/剔除行为不完全一致，所以轴看不到。</p>
 */
public final class StressInterfaceRenderer extends KineticBlockEntityRenderer<FactoryStressPortBlockEntity> {
    public StressInterfaceRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(FactoryStressPortBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
                              MultiBufferSource buffer, int light, int overlay) {
        Direction.Axis axis = ((IRotate) blockEntity.getBlockState().getBlock())
                .getRotationAxis(blockEntity.getBlockState());
        var shaftModel = getRotatedModel(blockEntity, shaft(axis));
        renderRotatingBuffer(blockEntity, shaftModel, poseStack,
                buffer.getBuffer(RenderType.solid()), light);
    }

    @Override
    public boolean shouldRenderOffScreen(FactoryStressPortBlockEntity blockEntity) {
        return true;
    }
}
