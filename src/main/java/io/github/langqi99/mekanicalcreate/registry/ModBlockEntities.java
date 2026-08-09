package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import net.neoforged.bus.api.IEventBus;

public final class ModBlockEntities {
    private static final TileEntityTypeDeferredRegister TYPES = new TileEntityTypeDeferredRegister(MekanicalCreate.MOD_ID);

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> SIMULATION_CHAMBER = TYPES
            .mekBuilder(ModBlocks.SIMULATION_CHAMBER, SimulationChamberBlockEntity::new)
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .withSimple(Capabilities.CONFIG_CARD)
            .build();

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> FLUID_MEKANICAL_FACTORY = TYPES
            .mekBuilder(ModBlocks.FLUID_MEKANICAL_FACTORY,
                    (pos, state) -> new SimulationChamberBlockEntity(
                            ModBlocks.FLUID_MEKANICAL_FACTORY, pos, state))
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .withSimple(Capabilities.CONFIG_CARD)
            .build();

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> BASIC_MEKANICAL_FACTORY = TYPES
            .mekBuilder(ModBlocks.BASIC_MEKANICAL_FACTORY,
                    (pos, state) -> new SimulationChamberBlockEntity(ModBlocks.BASIC_MEKANICAL_FACTORY, pos, state))
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .withSimple(Capabilities.CONFIG_CARD)
            .build();

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> ADVANCED_MEKANICAL_FACTORY = TYPES
            .mekBuilder(ModBlocks.ADVANCED_MEKANICAL_FACTORY,
                    (pos, state) -> new SimulationChamberBlockEntity(ModBlocks.ADVANCED_MEKANICAL_FACTORY, pos, state))
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .withSimple(Capabilities.CONFIG_CARD)
            .build();

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> ELITE_MEKANICAL_FACTORY = TYPES
            .mekBuilder(ModBlocks.ELITE_MEKANICAL_FACTORY,
                    (pos, state) -> new SimulationChamberBlockEntity(ModBlocks.ELITE_MEKANICAL_FACTORY, pos, state))
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .withSimple(Capabilities.CONFIG_CARD)
            .build();

    public static final TileEntityTypeRegistryObject<SimulationChamberBlockEntity> ULTIMATE_MEKANICAL_FACTORY = TYPES
            .mekBuilder(ModBlocks.ULTIMATE_MEKANICAL_FACTORY,
                    (pos, state) -> new SimulationChamberBlockEntity(ModBlocks.ULTIMATE_MEKANICAL_FACTORY, pos, state))
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .withSimple(Capabilities.CONFIG_CARD)
            .build();

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
