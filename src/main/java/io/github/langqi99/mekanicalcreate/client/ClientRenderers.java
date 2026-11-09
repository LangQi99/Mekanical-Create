package io.github.langqi99.mekanicalcreate.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.registry.ModBlockEntities;
import io.github.langqi99.mekanicalcreate.registry.ModStress;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renderer registration in a dedicated MOD-bus client subscriber (Create addon pattern). */
@EventBusSubscriber(modid = MekanicalCreate.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRenderers {
    static final PartialModel SHAFT_BEARING = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(MekanicalCreate.MOD_ID, "block/machine_shaft_bearing"));

    private ClientRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModStress.stressPortType(), StressInterfaceRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SIMULATION_CHAMBER.get(),
                MachineShaftHubRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BASIC_MEKANICAL_FACTORY.get(),
                MachineShaftHubRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ADVANCED_MEKANICAL_FACTORY.get(),
                MachineShaftHubRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ELITE_MEKANICAL_FACTORY.get(),
                MachineShaftHubRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ULTIMATE_MEKANICAL_FACTORY.get(),
                MachineShaftHubRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FLUID_MEKANICAL_FACTORY.get(),
                MachineShaftHubRenderer::new);
    }
}
