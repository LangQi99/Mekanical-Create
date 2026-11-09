package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.FactoryStressPortBlock;
import io.github.langqi99.mekanicalcreate.content.FactoryStressPortBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registrations for the multiblock factory stress port. */
public final class ModStress {
    public static final String STRESS_PORT_ID = "mekanical_factory_stress_port";

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MekanicalCreate.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MekanicalCreate.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MekanicalCreate.MOD_ID);

    public static final DeferredHolder<Block, FactoryStressPortBlock> STRESS_PORT = BLOCKS.register(
            STRESS_PORT_ID,
            () -> new FactoryStressPortBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredHolder<Item, BlockItem> STRESS_PORT_ITEM =
            ITEMS.registerSimpleBlockItem(STRESS_PORT_ID, STRESS_PORT);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> STRESS_PORT_BE =
            BLOCK_ENTITIES.register(STRESS_PORT_ID, () -> BlockEntityType.Builder
                    .of(FactoryStressPortBlockEntity::new, STRESS_PORT.get())
                    .build(null));

    private ModStress() {
    }

    @SuppressWarnings("unchecked")
    public static BlockEntityType<FactoryStressPortBlockEntity> stressPortType() {
        return (BlockEntityType<FactoryStressPortBlockEntity>) STRESS_PORT_BE.get();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
