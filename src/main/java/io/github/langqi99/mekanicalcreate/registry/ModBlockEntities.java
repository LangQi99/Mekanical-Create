package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ModBlockEntities {
    private static final TileEntityTypeDeferredRegister TYPES = new TileEntityTypeDeferredRegister(MekanicalCreate.MOD_ID);

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> SIMULATION_CHAMBER = TYPES
            .builder(ModBlocks.SIMULATION_CHAMBER, SimulationChamberBlockEntity::new)
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .build();

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> BASIC_MEKANICAL_FACTORY = TYPES
            .builder(ModBlocks.BASIC_MEKANICAL_FACTORY,
                    (pos, state) -> new SimulationChamberBlockEntity(ModBlocks.BASIC_MEKANICAL_FACTORY, pos, state))
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .build();

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> ADVANCED_MEKANICAL_FACTORY = TYPES
            .builder(ModBlocks.ADVANCED_MEKANICAL_FACTORY,
                    (pos, state) -> new SimulationChamberBlockEntity(ModBlocks.ADVANCED_MEKANICAL_FACTORY, pos, state))
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .build();

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> ELITE_MEKANICAL_FACTORY = TYPES
            .builder(ModBlocks.ELITE_MEKANICAL_FACTORY,
                    (pos, state) -> new SimulationChamberBlockEntity(ModBlocks.ELITE_MEKANICAL_FACTORY, pos, state))
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .build();

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> ULTIMATE_MEKANICAL_FACTORY = TYPES
            .builder(ModBlocks.ULTIMATE_MEKANICAL_FACTORY,
                    (pos, state) -> new SimulationChamberBlockEntity(ModBlocks.ULTIMATE_MEKANICAL_FACTORY, pos, state))
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .build();

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
