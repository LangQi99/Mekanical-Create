package io.github.langqi99.mekanicalcreate.content;

import java.util.function.UnaryOperator;
import mekanism.common.block.prefab.BlockBasicMultiblock;
import mekanism.common.content.blocktype.BlockTypeTile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;

/** A multiblock port whose visible block state mirrors its transfer mode. */
public final class BlockMekanicalFactoryPort extends BlockBasicMultiblock<MekanicalFactoryPortBlockEntity> {
    public static final EnumProperty<MekanicalFactoryPortMode> MODE =
            EnumProperty.create("mode", MekanicalFactoryPortMode.class);

    public BlockMekanicalFactoryPort(BlockTypeTile<MekanicalFactoryPortBlockEntity> type,
                                     UnaryOperator<BlockBehaviour.Properties> propertiesModifier) {
        super(type, propertiesModifier);
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE);
    }
}
