package io.github.langqi99.mekanicalcreate.content;

import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.UnaryOperator;

public class BlockMekanicalMachine<TILE extends TileEntityMekanism, TYPE extends BlockTypeTile<TILE>>
        extends BlockTile<TILE, TYPE> implements KineticMachineBlock {

    public BlockMekanicalMachine(TYPE type, UnaryOperator<BlockBehaviour.Properties> properties) {
        super(type, properties);
    }

    @Override
    public Direction shaftFace(BlockState state) {
        return Attribute.getFacing(state).getClockWise();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult placement = MachineShaftPlacement.tryPlace(stack, state, level, pos, player, hand, hit);
        return placement != null ? placement : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
