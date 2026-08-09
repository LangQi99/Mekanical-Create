package io.github.langqi99.mekanicalcreate.content;

import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import java.util.Collections;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.tile.base.SubstanceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** A mode-aware port that routes each capability to one colored factory channel. */
public final class MekanicalFactoryPortBlockEntity extends MekanicalFactoryBlockEntity {
    public MekanicalFactoryPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MEKANICAL_FACTORY_PORT, pos, state);
        delaySupplier = NO_DELAY;
    }

    @NotNull
    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return side -> switch (getMode()) {
            case INPUT -> getMultiblock().getInputFluidTanks();
            case OUTPUT -> getMultiblock().getOutputFluidTanks();
            case CATALYST, ENERGY -> Collections.emptyList();
        };
    }

    @NotNull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        return side -> getMode() == MekanicalFactoryPortMode.ENERGY
                ? getMultiblock().getEnergyContainers(side) : Collections.emptyList();
    }

    @NotNull
    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        return side -> switch (getMode()) {
            case CATALYST -> getMultiblock().getCatalystSlotsForPort();
            case INPUT -> getMultiblock().getInputSlotsForPort();
            case OUTPUT -> getMultiblock().getOutputSlotsForPort();
            case ENERGY -> Collections.emptyList();
        };
    }

    @Override
    public boolean persists(SubstanceType type) {
        if (type == SubstanceType.FLUID || type == SubstanceType.ENERGY) {
            return false;
        }
        return super.persists(type);
    }

    @Override
    public int getRedstoneLevel() {
        return getMultiblock().getCurrentRedstoneLevel();
    }

    public MekanicalFactoryPortMode getMode() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockMekanicalFactoryPort.MODE)
                ? state.getValue(BlockMekanicalFactoryPort.MODE)
                : MekanicalFactoryPortMode.INPUT;
    }

    private void setMode(MekanicalFactoryPortMode mode) {
        if (level != null && mode != getMode()) {
            level.setBlockAndUpdate(worldPosition,
                    getBlockState().setValue(BlockMekanicalFactoryPort.MODE, mode));
            invalidateCachedCapabilities();
        }
    }

    @Override
    public InteractionResult onRightClick(Player player) {
        if (!isRemote()) {
            player.displayClientMessage(Component.translatable(
                    "message.mekanicalcreate.port_mode", getMode().getTextComponent()), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakRightClick(Player player) {
        if (!isRemote()) {
            MekanicalFactoryPortMode next = getMode().next();
            setMode(next);
            player.displayClientMessage(Component.translatable(
                    "message.mekanicalcreate.port_mode_changed", next.getTextComponent()), true);
        }
        return InteractionResult.SUCCESS;
    }
}
