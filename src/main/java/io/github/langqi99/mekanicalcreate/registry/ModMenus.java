package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryControllerBlockEntity;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryContainer;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import net.neoforged.bus.api.IEventBus;

public final class ModMenus {
    private static final ContainerTypeDeferredRegister MENUS = new ContainerTypeDeferredRegister(MekanicalCreate.MOD_ID);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<SimulationChamberBlockEntity>> SIMULATION_CHAMBER = MENUS
            .custom("simulation_chamber", SimulationChamberBlockEntity.class)
            .offset(0, 36)
            .build();

    public static final ContainerTypeRegistryObject<MekanicalFactoryContainer> FLUID_MEKANICAL_FACTORY = MENUS
            .register("fluid_mekanical_factory", MekanicalFactoryControllerBlockEntity.class,
                    MekanicalFactoryContainer::new);

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
