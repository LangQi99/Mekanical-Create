package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import io.github.langqi99.mekanicalcreate.content.SuperSimulationChamberBlockEntity;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import net.neoforged.bus.api.IEventBus;

public final class ModBlockEntities {
    private static final TileEntityTypeDeferredRegister TYPES = new TileEntityTypeDeferredRegister(MekanicalCreate.MOD_ID);

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> SIMULATION_CHAMBER = TYPES
            .mekBuilder(ModBlocks.SIMULATION_CHAMBER, SimulationChamberBlockEntity::new)
            .serverTicker(TileEntityMekanism::tickServer)
            .withSimple(Capabilities.CONFIG_CARD)
            .build();

    public static final TileEntityTypeRegistryObject<SuperSimulationChamberBlockEntity> SUPER_SIMULATION_CHAMBER = TYPES
            .mekBuilder(ModBlocks.SUPER_SIMULATION_CHAMBER, SuperSimulationChamberBlockEntity::new)
            .serverTicker(TileEntityMekanism::tickServer)
            .withSimple(Capabilities.CONFIG_CARD)
            .build();

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
