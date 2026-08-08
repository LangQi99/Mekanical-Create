package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.ModLang;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import io.github.langqi99.mekanicalcreate.content.ModBlockShapes;
import java.util.EnumSet;
import mekanism.api.Upgrade;
import mekanism.api.tier.BaseTier;
import mekanism.common.block.attribute.AttributeStateFacing;
import mekanism.common.block.attribute.AttributeTier;
import mekanism.common.block.attribute.AttributeUpgradeable;
import mekanism.common.block.attribute.Attributes;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.content.blocktype.BlockTypeTile.BlockTileBuilder;
import mekanism.common.item.block.machine.ItemBlockMachine;
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

    public static final BlockTypeTile<SimulationChamberBlockEntity> BASIC_MEKANICAL_FACTORY_TYPE = BlockTileBuilder
            .createBlock(() -> ModBlockEntities.BASIC_MEKANICAL_FACTORY, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
            .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(BaseTier.BASIC),
                    () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(BaseTier.BASIC))
            .with(Attributes.ACTIVE_LIGHT, new AttributeStateFacing(), Attributes.INVENTORY,
                    Attributes.SECURITY, Attributes.REDSTONE, Attributes.COMPARATOR,
                    new AttributeTier<>(FactoryTier.BASIC),
                    new AttributeUpgradeable(() -> ModBlocks.ADVANCED_MEKANICAL_FACTORY))
            .withSupportedUpgrades(EnumSet.of(Upgrade.SPEED, Upgrade.ENERGY, Upgrade.MUFFLING))
            .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, BlockTypeTile<SimulationChamberBlockEntity>>,
            ItemBlockMachine> BASIC_MEKANICAL_FACTORY =
            BLOCKS.register("basic_mekanical_factory",
                    () -> new BlockTile<>(BASIC_MEKANICAL_FACTORY_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    ItemBlockMachine::new);

    public static final BlockTypeTile<SimulationChamberBlockEntity> ADVANCED_MEKANICAL_FACTORY_TYPE = BlockTileBuilder
            .createBlock(() -> ModBlockEntities.ADVANCED_MEKANICAL_FACTORY, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
            .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(BaseTier.ADVANCED),
                    () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(BaseTier.ADVANCED))
            .with(Attributes.ACTIVE_LIGHT, new AttributeStateFacing(), Attributes.INVENTORY,
                    Attributes.SECURITY, Attributes.REDSTONE, Attributes.COMPARATOR,
                    new AttributeTier<>(FactoryTier.ADVANCED),
                    new AttributeUpgradeable(() -> ModBlocks.ELITE_MEKANICAL_FACTORY))
            .withSupportedUpgrades(EnumSet.of(Upgrade.SPEED, Upgrade.ENERGY, Upgrade.MUFFLING))
            .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, BlockTypeTile<SimulationChamberBlockEntity>>,
            ItemBlockMachine> ADVANCED_MEKANICAL_FACTORY =
            BLOCKS.register("advanced_mekanical_factory",
                    () -> new BlockTile<>(ADVANCED_MEKANICAL_FACTORY_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    ItemBlockMachine::new);

    public static final BlockTypeTile<SimulationChamberBlockEntity> ELITE_MEKANICAL_FACTORY_TYPE = BlockTileBuilder
            .createBlock(() -> ModBlockEntities.ELITE_MEKANICAL_FACTORY, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
            .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(BaseTier.ELITE),
                    () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(BaseTier.ELITE))
            .with(Attributes.ACTIVE_LIGHT, new AttributeStateFacing(), Attributes.INVENTORY,
                    Attributes.SECURITY, Attributes.REDSTONE, Attributes.COMPARATOR,
                    new AttributeTier<>(FactoryTier.ELITE),
                    new AttributeUpgradeable(() -> ModBlocks.ULTIMATE_MEKANICAL_FACTORY))
            .withSupportedUpgrades(EnumSet.of(Upgrade.SPEED, Upgrade.ENERGY, Upgrade.MUFFLING))
            .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, BlockTypeTile<SimulationChamberBlockEntity>>,
            ItemBlockMachine> ELITE_MEKANICAL_FACTORY =
            BLOCKS.register("elite_mekanical_factory",
                    () -> new BlockTile<>(ELITE_MEKANICAL_FACTORY_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    ItemBlockMachine::new);

    public static final BlockTypeTile<SimulationChamberBlockEntity> ULTIMATE_MEKANICAL_FACTORY_TYPE = BlockTileBuilder
            .createBlock(() -> ModBlockEntities.ULTIMATE_MEKANICAL_FACTORY, ModLang.DESCRIPTION_SIMULATION_CHAMBER)
            .withGui(() -> ModMenus.SIMULATION_CHAMBER)
            .withSound(MekanismSounds.ENRICHMENT_CHAMBER)
            .withEnergyConfig(() -> SimulationChamberBlockEntity.getBaseEnergyUsage(BaseTier.ULTIMATE),
                    () -> SimulationChamberBlockEntity.getBaseEnergyCapacity(BaseTier.ULTIMATE))
            .with(Attributes.ACTIVE_LIGHT, new AttributeStateFacing(), Attributes.INVENTORY,
                    Attributes.SECURITY, Attributes.REDSTONE, Attributes.COMPARATOR,
                    new AttributeTier<>(FactoryTier.ULTIMATE))
            .withSupportedUpgrades(EnumSet.of(Upgrade.SPEED, Upgrade.ENERGY, Upgrade.MUFFLING))
            .withCustomShape(ModBlockShapes.SIMULATION_CHAMBER)
            .build();

    public static final BlockRegistryObject<
            BlockTile<SimulationChamberBlockEntity, BlockTypeTile<SimulationChamberBlockEntity>>,
            ItemBlockMachine> ULTIMATE_MEKANICAL_FACTORY =
            BLOCKS.register("ultimate_mekanical_factory",
                    () -> new BlockTile<>(ULTIMATE_MEKANICAL_FACTORY_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                    ItemBlockMachine::new);

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
