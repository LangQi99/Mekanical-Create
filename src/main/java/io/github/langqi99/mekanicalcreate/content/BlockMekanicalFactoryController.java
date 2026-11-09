package io.github.langqi99.mekanicalcreate.content;

import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.prefab.BlockBasicMultiblock;
import mekanism.common.content.blocktype.BlockTypeTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public final class BlockMekanicalFactoryController extends BlockBasicMultiblock<MekanicalFactoryControllerBlockEntity>
        implements KineticMachineBlock {

    public BlockMekanicalFactoryController(BlockTypeTile<MekanicalFactoryControllerBlockEntity> type) {
        super(type, properties -> properties.mapColor(MapColor.METAL));
    }

    @Override
    public Direction shaftFace(BlockState state) {
        return Attribute.getFacing(state);
    }

    @Override
    public boolean hasOppositeShaft() {
        return false;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (isPlacingShaft(stack, level, pos, state, hit.getDirection())) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
