package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.ModLang;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import io.github.langqi99.mekanicalcreate.content.ModBlockShapes;
import mekanism.api.tier.BaseTier;
import mekanism.common.attachments.component.AttachedEjector;
import mekanism.common.attachments.component.AttachedSideConfig;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.attachments.containers.item.ItemSlotsBuilder;
import mekanism.common.block.attribute.AttributeTier;
import mekanism.common.block.attribute.AttributeUpgradeable;
import mekanism.common.block.attribute.AttributeUpgradeSupport;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.content.blocktype.Machine.MachineBuilder;
import mekanism.common.item.block.ItemBlockTooltip;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ItemRegistryObject;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.registries.MekanismSounds;
import mekanism.common.tier.FactoryTier;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;

public final class ModBlocks {
    private static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(MekanicalCreate.MOD_ID);

    public static final Machine<SimulationChamberBlockEntity> SIMULATION_CHAMBER_TYPE = MachineBuilder
            .createMachine(() -> ModBlockEntities.SIMULATION_CHAMBER, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
            .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(null),
                    () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(null))
            .with(AttributeUpgradeSupport.DEFAULT_MACHINE_UPGRADES,
                    new AttributeUpgradeable(() -> ModBlocks.BASIC_MEKANICAL_FACTORY))
            .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
            .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>,
            ItemBlockTooltip<BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>>> SIMULATION_CHAMBER =
            BLOCKS.register("simulation_chamber",
                    () -> new BlockTile<>(SIMULATION_CHAMBER_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    (block, properties) -> new ItemBlockTooltip<>(block, true, properties
                            .component(MekanismDataComponents.EJECTOR, AttachedEjector.DEFAULT)
                            .component(MekanismDataComponents.SIDE_CONFIG, AttachedSideConfig.EXTRA_MACHINE)))
                    .forItemHolder(holder -> addItemAttachments(holder));

    public static final Machine<SimulationChamberBlockEntity> BASIC_MEKANICAL_FACTORY_TYPE = MachineBuilder
            .createMachine(() -> ModBlockEntities.BASIC_MEKANICAL_FACTORY, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
            .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(BaseTier.BASIC),
                    () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(BaseTier.BASIC))
            .with(AttributeUpgradeSupport.DEFAULT_MACHINE_UPGRADES, new AttributeTier<>(FactoryTier.BASIC),
                    new AttributeUpgradeable(() -> ModBlocks.ADVANCED_MEKANICAL_FACTORY))
            .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
            .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>,
            ItemBlockTooltip<BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>>> BASIC_MEKANICAL_FACTORY =
            BLOCKS.register("basic_mekanical_factory",
                    () -> new BlockTile<>(BASIC_MEKANICAL_FACTORY_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    (block, properties) -> new ItemBlockTooltip<>(block, true, properties
                            .component(MekanismDataComponents.EJECTOR, AttachedEjector.DEFAULT)
                            .component(MekanismDataComponents.SIDE_CONFIG, AttachedSideConfig.EXTRA_MACHINE)))
                    .forItemHolder(holder -> addItemAttachments(holder));

    public static final Machine<SimulationChamberBlockEntity> ADVANCED_MEKANICAL_FACTORY_TYPE = MachineBuilder
            .createMachine(() -> ModBlockEntities.ADVANCED_MEKANICAL_FACTORY, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
            .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(BaseTier.ADVANCED),
                    () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(BaseTier.ADVANCED))
            .with(AttributeUpgradeSupport.DEFAULT_MACHINE_UPGRADES, new AttributeTier<>(FactoryTier.ADVANCED),
                    new AttributeUpgradeable(() -> ModBlocks.ELITE_MEKANICAL_FACTORY))
            .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
            .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>,
            ItemBlockTooltip<BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>>> ADVANCED_MEKANICAL_FACTORY =
            BLOCKS.register("advanced_mekanical_factory",
                    () -> new BlockTile<>(ADVANCED_MEKANICAL_FACTORY_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    (block, properties) -> new ItemBlockTooltip<>(block, true, properties
                            .component(MekanismDataComponents.EJECTOR, AttachedEjector.DEFAULT)
                            .component(MekanismDataComponents.SIDE_CONFIG, AttachedSideConfig.EXTRA_MACHINE)))
                    .forItemHolder(holder -> addItemAttachments(holder));

    public static final Machine<SimulationChamberBlockEntity> ELITE_MEKANICAL_FACTORY_TYPE = MachineBuilder
            .createMachine(() -> ModBlockEntities.ELITE_MEKANICAL_FACTORY, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
            .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(BaseTier.ELITE),
                    () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(BaseTier.ELITE))
            .with(AttributeUpgradeSupport.DEFAULT_MACHINE_UPGRADES, new AttributeTier<>(FactoryTier.ELITE),
                    new AttributeUpgradeable(() -> ModBlocks.ULTIMATE_MEKANICAL_FACTORY))
            .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
            .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>,
            ItemBlockTooltip<BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>>> ELITE_MEKANICAL_FACTORY =
            BLOCKS.register("elite_mekanical_factory",
                    () -> new BlockTile<>(ELITE_MEKANICAL_FACTORY_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    (block, properties) -> new ItemBlockTooltip<>(block, true, properties
                            .component(MekanismDataComponents.EJECTOR, AttachedEjector.DEFAULT)
                            .component(MekanismDataComponents.SIDE_CONFIG, AttachedSideConfig.EXTRA_MACHINE)))
                    .forItemHolder(holder -> addItemAttachments(holder));

    public static final Machine<SimulationChamberBlockEntity> ULTIMATE_MEKANICAL_FACTORY_TYPE = MachineBuilder
            .createMachine(() -> ModBlockEntities.ULTIMATE_MEKANICAL_FACTORY, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
            .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(BaseTier.ULTIMATE),
                    () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(BaseTier.ULTIMATE))
            .with(AttributeUpgradeSupport.DEFAULT_MACHINE_UPGRADES, new AttributeTier<>(FactoryTier.ULTIMATE))
            .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
            .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>,
            ItemBlockTooltip<BlockTile<SimulationChamberBlockEntity, Machine<SimulationChamberBlockEntity>>>> ULTIMATE_MEKANICAL_FACTORY =
            BLOCKS.register("ultimate_mekanical_factory",
                    () -> new BlockTile<>(ULTIMATE_MEKANICAL_FACTORY_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    (block, properties) -> new ItemBlockTooltip<>(block, true, properties
                            .component(MekanismDataComponents.EJECTOR, AttachedEjector.DEFAULT)
                            .component(MekanismDataComponents.SIDE_CONFIG, AttachedSideConfig.EXTRA_MACHINE)))
                    .forItemHolder(holder -> addItemAttachments(holder));

    private static void addItemAttachments(ItemRegistryObject<? extends ItemBlockTooltip<?>> holder) {
        holder.addAttachmentOnlyContainers(ContainerType.ITEM, () -> ItemSlotsBuilder.builder()
                .addBasic(2)
                .addInput(SimulationChamberBlockEntity.INPUT_COUNT)
                .addOutput(SimulationChamberBlockEntity.OUTPUT_COUNT)
                .addEnergy()
                .build());
    }

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
