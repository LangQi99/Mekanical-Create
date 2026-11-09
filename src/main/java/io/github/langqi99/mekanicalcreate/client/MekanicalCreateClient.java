package io.github.langqi99.mekanicalcreate.client;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.client.ponder.MekanicalCreatePonderPlugin;
import io.github.langqi99.mekanicalcreate.registry.ModMenus;
import io.github.langqi99.mekanicalcreate.registry.ModStress;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import io.github.langqi99.mekanicalcreate.registry.ModBlockEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = MekanicalCreate.MOD_ID, dist = Dist.CLIENT)
public final class MekanicalCreateClient {
    public MekanicalCreateClient(IEventBus modBus) {
        PonderIndex.addPlugin(new MekanicalCreatePonderPlugin());
        modBus.addListener(this::registerScreens);
    }


    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(io.github.langqi99.mekanicalcreate.registry.ModMenus.STRESS_INTERFACE.get(), StressInterfaceScreen::new);
        event.register(ModMenus.SIMULATION_CHAMBER.get(), SimulationChamberScreen::new);
        event.register(ModMenus.FLUID_MEKANICAL_FACTORY.get(), FluidMekanicalFactoryScreen::new);
    }
}
