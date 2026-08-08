package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import io.github.langqi99.mekanicalcreate.content.SuperSimulationChamberBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import net.neoforged.bus.api.IEventBus;

public final class ModMenus {
    private static final ContainerTypeDeferredRegister MENUS = new ContainerTypeDeferredRegister(MekanicalCreate.MOD_ID);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<SimulationChamberBlockEntity>> SIMULATION_CHAMBER = MENUS
            .custom("simulation_chamber", SimulationChamberBlockEntity.class)
            .offset(0, 18)
            .build();

    public static final ContainerTypeRegistryObject<MekanismTileContainer<SuperSimulationChamberBlockEntity>> SUPER_SIMULATION_CHAMBER = MENUS
            .custom("super_simulation_chamber", SuperSimulationChamberBlockEntity.class)
            .offset(0, 18)
            .build();

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
