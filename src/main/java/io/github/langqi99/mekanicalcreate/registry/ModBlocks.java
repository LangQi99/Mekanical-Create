package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.ModLang;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import mekanism.common.attachments.component.AttachedEjector;
import mekanism.common.attachments.component.AttachedSideConfig;
import mekanism.common.block.attribute.AttributeUpgradeSupport;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.content.blocktype.Machine.MachineBuilder;
import mekanism.common.item.block.ItemBlockTooltip;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registries.MekanismDataComponents;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;

public final class ModBlocks {
    private static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(MekanicalCreate.MOD_ID);

    public static final Machine<SimulationChamberBlockEntity> SIMULATION_CHAMBER_TYPE = MachineBuilder
            .createMachine(() -> ModBlockEntities.SIMULATION_CHAMBER, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withEnergyConfig(SimulationChamberBlockEntity::getBaseEnergyUsage,
                    SimulationChamberBlockEntity::getBaseEnergyCapacity)
            .with(AttributeUpgradeSupport.SPEED_ENERGY)
            .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>,
            ItemBlockTooltip<BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>>> SIMULATION_CHAMBER =
            BLOCKS.register("simulation_chamber",
                    () -> new BlockTile<>(SIMULATION_CHAMBER_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    (block, properties) -> new ItemBlockTooltip<>(block, true, properties
                            .component(MekanismDataComponents.EJECTOR, AttachedEjector.DEFAULT)
                            .component(MekanismDataComponents.SIDE_CONFIG, AttachedSideConfig.EXTRA_MACHINE)));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
