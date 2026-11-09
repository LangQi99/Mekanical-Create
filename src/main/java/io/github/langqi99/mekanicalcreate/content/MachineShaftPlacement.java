package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import io.github.langqi99.mekanicalcreate.client.ShaftPlacementPreview;
import java.util.function.Predicate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class MachineShaftPlacement implements IPlacementHelper {
    private static final MachineShaftPlacement INSTANCE = new MachineShaftPlacement();

    private MachineShaftPlacement() {
    }

    public static void register() {
        PlacementHelpers.register(INSTANCE);
    }

    @Nullable
    static ItemInteractionResult tryPlace(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() || !player.mayBuild() || !(stack.getItem() instanceof BlockItem item)
                || !INSTANCE.matchesItem(stack) || !isSocket(level, pos, state, hit.getDirection())) {
            return null;
        }
        return INSTANCE.getOffset(player, level, state, pos, hit)
                .placeInWorld(level, item, player, hand, hit);
    }

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return shaftHelper().getItemPredicate();
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.getBlock() instanceof KineticMachineBlock;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction face = hit.getDirection();
        if (!isSocket(level, pos, state, face) || player != null && !player.mayBuild()) {
            return PlacementOffset.fail();
        }
        // Use Create's normal range, shaft chain traversal and powered shaft transform.
        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, face.getAxis());
        PlacementOffset offset = shaftHelper().getOffset(player, level, shaft, pos, hit);
        if (!offset.isSuccessful()) {
            return offset;
        }
        // A blocked socket must not preview or place a shaft on the other side of the machine.
        BlockPos delta = offset.getBlockPos().subtract(pos);
        return delta.get(face.getAxis()) * face.getAxisDirection().getStep() > 0
                ? offset : PlacementOffset.fail();
    }

    @Override
    public void renderAt(BlockPos pos, BlockState state, BlockHitResult hit, PlacementOffset offset) {
        ShaftPlacementPreview.show(this, offset);
    }

    private static IPlacementHelper shaftHelper() {
        return PlacementHelpers.get(ShaftBlock.placementHelperId);
    }

    private static boolean isSocket(Level level, BlockPos pos, BlockState state, Direction face) {
        return state.getBlock() instanceof KineticMachineBlock machine
                && machine.hasShaftTowards(level, pos, state, face);
    }
}
