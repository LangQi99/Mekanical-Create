package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.ModLang;
import io.github.langqi99.mekanicalcreate.content.BlockMekanicalFactoryPort;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryCasingBlockEntity;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryControllerBlockEntity;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryPortBlockEntity;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryUpgradeCoreBlockEntity;
import io.github.langqi99.mekanicalcreate.content.ModBlockShapes;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import java.util.EnumSet;
import mekanism.api.Upgrade;
import mekanism.api.tier.BaseTier;
import mekanism.common.block.attribute.AttributeStateFacing;
import mekanism.common.block.attribute.AttributeTier;
import mekanism.common.block.attribute.AttributeUpgradeable;
import mekanism.common.block.attribute.Attributes;
import mekanism.common.block.prefab.BlockBasicMultiblock;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.content.blocktype.BlockTypeTile.BlockTileBuilder;
import mekanism.common.item.block.machine.ItemBlockMachine;
import mekanism.common.item.block.ItemBlockTooltip;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registries.MekanismSounds;
import mekanism.common.tier.FactoryTier;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ModBlocks {
    private static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(MekanicalCreate.MOD_ID);

    public static final BlockTypeTile<SimulationChamberBlockEntity> SIMULATION_CHAMBER_TYPE = BlockTileBuilder
            .createBlock(() -> ModBlockEntities.SIMULATION_CHAMBER, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
            .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(null),
                    () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(null))
            .with(Attributes.ACTIVE_LIGHT, new AttributeStateFacing(), Attributes.INVENTORY,
                    Attributes.SECURITY, Attributes.REDSTONE, Attributes.COMPARATOR,
                    new AttributeUpgradeable(() -> ModBlocks.BASIC_MEKANICAL_FACTORY))
            .withSupportedUpgrades(EnumSet.of(Upgrade.SPEED, Upgrade.ENERGY, Upgrade.MUFFLING))
            .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, BlockTypeTile<SimulationChamberBlockEntity>>,
            ItemBlockMachine> SIMULATION_CHAMBER =
            BLOCKS.register("simulation_chamber",
                    () -> new BlockTile<>(SIMULATION_CHAMBER_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    ItemBlockMachine::new);

    public static final BlockTypeTile<MekanicalFactoryControllerBlockEntity> FLUID_MEKANICAL_FACTORY_TYPE =
            BlockTileBuilder
                    .createBlock(() -> ModBlockEntities.FLUID_MEKANICAL_FACTORY,
                            ModLang.DESCRIPTION_FLUID_MEKANICAL_FACTORY)
                    .withGui(() -> ModMenus.FLUID_MEKANICAL_FACTORY)
                    .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
                    .with(Attributes.INVENTORY, Attributes.ACTIVE, new AttributeStateFacing())
                    .externalMultiblock()
                    .build();

    public static final BlockRegistryObject<
            BlockBasicMultiblock<MekanicalFactoryControllerBlockEntity>,
            ItemBlockTooltip<BlockBasicMultiblock<MekanicalFactoryControllerBlockEntity>>> FLUID_MEKANICAL_FACTORY =
            BLOCKS.register("fluid_mekanical_factory",
                    () -> new BlockBasicMultiblock<>(FLUID_MEKANICAL_FACTORY_TYPE,
                            properties -> properties.mapColor(MapColor.METAL)),
                    block -> new ItemBlockTooltip<>(block));

    public static final BlockTypeTile<MekanicalFactoryCasingBlockEntity> MEKANICAL_FACTORY_CASING_TYPE =
            BlockTileBuilder
                    .createBlock(() -> ModBlockEntities.MEKANICAL_FACTORY_CASING,
                            ModLang.DESCRIPTION_MEKANICAL_FACTORY_CASING)
                    .externalMultiblock()
                    .build();

    public static final BlockRegistryObject<
            BlockBasicMultiblock<MekanicalFactoryCasingBlockEntity>,
            ItemBlockTooltip<BlockBasicMultiblock<MekanicalFactoryCasingBlockEntity>>> MEKANICAL_FACTORY_CASING =
            BLOCKS.register("mekanical_factory_casing",
                    () -> new BlockBasicMultiblock<>(MEKANICAL_FACTORY_CASING_TYPE,
                            properties -> properties.mapColor(MapColor.METAL)),
                    block -> new ItemBlockTooltip<>(block));

    public static final BlockTypeTile<MekanicalFactoryPortBlockEntity> MEKANICAL_FACTORY_PORT_TYPE =
            BlockTileBuilder
                    .createBlock(() -> ModBlockEntities.MEKANICAL_FACTORY_PORT,
                            ModLang.DESCRIPTION_MEKANICAL_FACTORY_PORT)
                    .with(Attributes.INVENTORY, Attributes.COMPARATOR)
                    .externalMultiblock()
                    .build();

    public static final BlockRegistryObject<BlockMekanicalFactoryPort,
            ItemBlockTooltip<BlockMekanicalFactoryPort>> MEKANICAL_FACTORY_PORT =
            BLOCKS.register("mekanical_factory_port",
                    () -> new BlockMekanicalFactoryPort(MEKANICAL_FACTORY_PORT_TYPE,
                            properties -> properties.mapColor(MapColor.METAL)),
                    block -> new ItemBlockTooltip<>(block));

    public static final BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity> MEKANICAL_FACTORY_SPEED_CORE_TYPE =
            coreType(() -> ModBlockEntities.MEKANICAL_FACTORY_SPEED_CORE,
                    ModLang.DESCRIPTION_MEKANICAL_FACTORY_SPEED_CORE);
    public static final BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity> MEKANICAL_FACTORY_ENERGY_CORE_TYPE =
            coreType(() -> ModBlockEntities.MEKANICAL_FACTORY_ENERGY_CORE,
                    ModLang.DESCRIPTION_MEKANICAL_FACTORY_ENERGY_CORE);
    public static final BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity> MEKANICAL_FACTORY_FLUID_CORE_TYPE =
            coreType(() -> ModBlockEntities.MEKANICAL_FACTORY_FLUID_CORE,
                    ModLang.DESCRIPTION_MEKANICAL_FACTORY_FLUID_CORE);
    public static final BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity> MEKANICAL_FACTORY_CATALYST_CORE_TYPE =
            coreType(() -> ModBlockEntities.MEKANICAL_FACTORY_CATALYST_CORE,
                    ModLang.DESCRIPTION_MEKANICAL_FACTORY_CATALYST_CORE);

    public static final BlockRegistryObject<
            BlockTile<MekanicalFactoryUpgradeCoreBlockEntity, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity>>,
            ItemBlockTooltip<BlockTile<MekanicalFactoryUpgradeCoreBlockEntity, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity>>>> MEKANICAL_FACTORY_SPEED_CORE =
            registerCore("mekanical_factory_speed_core", MEKANICAL_FACTORY_SPEED_CORE_TYPE);
    public static final BlockRegistryObject<
            BlockTile<MekanicalFactoryUpgradeCoreBlockEntity, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity>>,
            ItemBlockTooltip<BlockTile<MekanicalFactoryUpgradeCoreBlockEntity, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity>>>> MEKANICAL_FACTORY_ENERGY_CORE =
            registerCore("mekanical_factory_energy_core", MEKANICAL_FACTORY_ENERGY_CORE_TYPE);
    public static final BlockRegistryObject<
            BlockTile<MekanicalFactoryUpgradeCoreBlockEntity, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity>>,
            ItemBlockTooltip<BlockTile<MekanicalFactoryUpgradeCoreBlockEntity, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity>>>> MEKANICAL_FACTORY_FLUID_CORE =
            registerCore("mekanical_factory_fluid_core", MEKANICAL_FACTORY_FLUID_CORE_TYPE);
    public static final BlockRegistryObject<
            BlockTile<MekanicalFactoryUpgradeCoreBlockEntity, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity>>,
            ItemBlockTooltip<BlockTile<MekanicalFactoryUpgradeCoreBlockEntity, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity>>>> MEKANICAL_FACTORY_CATALYST_CORE =
            registerCore("mekanical_factory_catalyst_core", MEKANICAL_FACTORY_CATALYST_CORE_TYPE);

    public static final BlockTypeTile<SimulationChamberBlockEntity> BASIC_MEKANICAL_FACTORY_TYPE = factoryType(
            BaseTier.BASIC, FactoryTier.BASIC, () -> ModBlockEntities.BASIC_MEKANICAL_FACTORY,
            () -> ModBlocks.ADVANCED_MEKANICAL_FACTORY);
    public static final BlockTypeTile<SimulationChamberBlockEntity> ADVANCED_MEKANICAL_FACTORY_TYPE = factoryType(
            BaseTier.ADVANCED, FactoryTier.ADVANCED, () -> ModBlockEntities.ADVANCED_MEKANICAL_FACTORY,
            () -> ModBlocks.ELITE_MEKANICAL_FACTORY);
    public static final BlockTypeTile<SimulationChamberBlockEntity> ELITE_MEKANICAL_FACTORY_TYPE = factoryType(
            BaseTier.ELITE, FactoryTier.ELITE, () -> ModBlockEntities.ELITE_MEKANICAL_FACTORY,
            () -> ModBlocks.ULTIMATE_MEKANICAL_FACTORY);
    public static final BlockTypeTile<SimulationChamberBlockEntity> ULTIMATE_MEKANICAL_FACTORY_TYPE = factoryType(
            BaseTier.ULTIMATE, FactoryTier.ULTIMATE, () -> ModBlockEntities.ULTIMATE_MEKANICAL_FACTORY, null);

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, BlockTypeTile<SimulationChamberBlockEntity>>,
            ItemBlockMachine> BASIC_MEKANICAL_FACTORY = registerFactory("basic_mekanical_factory",
            BASIC_MEKANICAL_FACTORY_TYPE);
    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, BlockTypeTile<SimulationChamberBlockEntity>>,
            ItemBlockMachine> ADVANCED_MEKANICAL_FACTORY = registerFactory("advanced_mekanical_factory",
            ADVANCED_MEKANICAL_FACTORY_TYPE);
    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, BlockTypeTile<SimulationChamberBlockEntity>>,
            ItemBlockMachine> ELITE_MEKANICAL_FACTORY = registerFactory("elite_mekanical_factory",
            ELITE_MEKANICAL_FACTORY_TYPE);
    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, BlockTypeTile<SimulationChamberBlockEntity>>,
            ItemBlockMachine> ULTIMATE_MEKANICAL_FACTORY = registerFactory("ultimate_mekanical_factory",
            ULTIMATE_MEKANICAL_FACTORY_TYPE);

    private static BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity> coreType(
            java.util.function.Supplier<mekanism.common.registration.impl.TileEntityTypeRegistryObject<MekanicalFactoryUpgradeCoreBlockEntity>> tile,
            mekanism.api.text.ILangEntry description) {
        return BlockTileBuilder.createBlock(tile, description).internalMultiblock().build();
    }

    private static BlockRegistryObject<
            BlockTile<MekanicalFactoryUpgradeCoreBlockEntity, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity>>,
            ItemBlockTooltip<BlockTile<MekanicalFactoryUpgradeCoreBlockEntity, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity>>>>
    registerCore(String name, BlockTypeTile<MekanicalFactoryUpgradeCoreBlockEntity> type) {
        return BLOCKS.register(name,
                () -> new BlockTile<>(type, properties -> properties.mapColor(MapColor.METAL)),
                block -> new ItemBlockTooltip<>(block));
    }

    private static BlockTypeTile<SimulationChamberBlockEntity> factoryType(BaseTier baseTier, FactoryTier factoryTier,
            java.util.function.Supplier<mekanism.common.registration.impl.TileEntityTypeRegistryObject<SimulationChamberBlockEntity>> tile,
            java.util.function.Supplier<BlockRegistryObject<?, ?>> upgradeTarget) {
        BlockTileBuilder<BlockTypeTile<SimulationChamberBlockEntity>, SimulationChamberBlockEntity, ?> builder =
                BlockTileBuilder.createBlock(tile, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
                        .withGui(() -> ModMenus.SIMULATION_CHAMBER)
                        .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
                        .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(baseTier),
                                () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(baseTier));
        if (upgradeTarget == null) {
            builder.with(Attributes.ACTIVE_LIGHT, new AttributeStateFacing(), Attributes.INVENTORY,
                    Attributes.SECURITY, Attributes.REDSTONE, Attributes.COMPARATOR,
                    new AttributeTier<>(factoryTier));
        } else {
            builder.with(Attributes.ACTIVE_LIGHT, new AttributeStateFacing(), Attributes.INVENTORY,
                    Attributes.SECURITY, Attributes.REDSTONE, Attributes.COMPARATOR,
                    new AttributeTier<>(factoryTier), new AttributeUpgradeable(upgradeTarget));
        }
        return builder.withSupportedUpgrades(EnumSet.of(Upgrade.SPEED, Upgrade.ENERGY, Upgrade.MUFFLING))
                .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER).build();
    }

    private static BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, BlockTypeTile<SimulationChamberBlockEntity>>,
            ItemBlockMachine> registerFactory(String name, BlockTypeTile<SimulationChamberBlockEntity> type) {
        return BLOCKS.register(name,
                () -> new BlockTile<>(type, properties -> properties.mapColor(MapColor.METAL)),
                ItemBlockMachine::new);
    }

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
