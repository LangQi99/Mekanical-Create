package io.github.langqi99.mekanicalcreate.content;

import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import java.util.List;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class MekanicalFactoryControllerBlockEntity extends MekanicalFactoryBlockEntity implements KineticMachine {
    private final MachineShaft shaft = new MachineShaft(this);

    public MekanicalFactoryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FLUID_MEKANICAL_FACTORY, pos, state);
        delaySupplier = NO_DELAY;
    }

    @Override
    protected boolean onUpdateServer(MekanicalFactoryMultiblockData multiblock) {
        boolean wasActive = getActive();
        setActive(multiblock.isFormed() && multiblock.isActive());
        return wasActive != getActive();
    }

    @Override
    public boolean canBeMaster() {
        return true;
    }

    public IEnergyContainer getEnergyContainer() {
        return getMultiblock().getEnergyContainer();
    }

    public List<InputInventorySlot> getInputSlots() {
        return getMultiblock().getInputSlots();
    }

    public List<IInventorySlot> getCatalystSlots() {
        return getMultiblock().getCatalystSlots();
    }

    public List<IInventorySlot> getOutputSlots() {
        return getMultiblock().getOutputSlotsForPort();
    }

    public List<IExtendedFluidTank> getInputFluidTanks() {
        return getMultiblock().getInputFluidTanks();
    }

    public List<IExtendedFluidTank> getAllInputFluidTanks() {
        return getMultiblock().getAllInputFluidTanks();
    }

    public List<IExtendedFluidTank> getOutputFluidTanks() {
        return getMultiblock().getOutputFluidTanks();
    }

    public List<IExtendedFluidTank> getAllOutputFluidTanks() {
        return getMultiblock().getAllOutputFluidTanks();
    }

    public List<IExtendedFluidTank> getActiveFluidTanks() {
        return getMultiblock().getActiveFluidTanks();
    }

    public int getFluidTankCount() {
        return getMultiblock().getFluidTankCount();
    }

    public int getCatalystSlotCount() {
        return getMultiblock().getCatalystSlotCount();
    }

    public long getEnergyPerTick() {
        return getMultiblock().getEnergyPerTick();
    }

    public double getScaledProgress() {
        return getMultiblock().getScaledProgress();
    }

    public boolean isEnergyStarved() {
        return getMultiblock().isEnergyStarved();
    }

    @Override
    public MachineShaft shaft() {
        return shaft;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        shaft.setLevel(level);
    }

    @Override
    public void setBlockState(BlockState state) {
        shaft.updateBlockState(state);
        super.setBlockState(state);
    }

    @Override
    public void onChunkUnloaded() {
        shaft.onChunkUnloaded();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        shaft.setRemoved();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        shaft.clearRemoved();
    }

    @Override
    protected void onUpdateClient() {
        super.onUpdateClient();
        shaft.tick();
    }

    @Override
    public CompoundTag getReducedUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getReducedUpdateTag(provider);
        shaft.saveToMachine(tag, provider, true);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        shaft.loadFromMachine(tag, provider, true);
    }

    @Override
    protected boolean onUpdateServer() {
        shaft.tick();
        boolean needsPacket = super.onUpdateServer();
        if (!getMultiblock().isFormed()) {
            shaft.setDemand(0);
        }
        return needsPacket;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        shaft.saveToMachine(tag, provider, false);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        shaft.loadFromMachine(tag, provider, false);
    }
}
