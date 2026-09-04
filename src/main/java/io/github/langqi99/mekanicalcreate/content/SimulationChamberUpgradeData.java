package io.github.langqi99.mekanicalcreate.content;

import java.util.List;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import mekanism.common.upgrade.MachineUpgradeData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Mekanism replaces the block before asking the new tile to parse its upgrade
 * data. Keep detached NBT snapshots in addition to the standard factory data
 * so removal hooks cannot empty the old slot objects before restoration.
 */
final class SimulationChamberUpgradeData extends MachineUpgradeData {
    final long storedEnergy;
    final CompoundTag energySlotData;
    final List<CompoundTag> inputSlotData;
    final List<CompoundTag> outputSlotData;
    final List<FluidStack> fluids;

    SimulationChamberUpgradeData(HolderLookup.Provider provider, boolean redstone,
                                 RedstoneControl controlType, IEnergyContainer energyContainer,
                                 int[] progress, EnergyInventorySlot energySlot,
                                 List<IInventorySlot> inputSlots, List<IInventorySlot> outputSlots,
                                 List<IExtendedFluidTank> fluidTanks,
                                 List<ITileComponent> components) {
        super(provider, redstone, controlType, energyContainer, progress, energySlot,
                inputSlots, outputSlots, false, components);
        storedEnergy = energyContainer.getEnergy();
        energySlotData = energySlot.serializeNBT(provider).copy();
        inputSlotData = inputSlots.stream()
                .map(slot -> slot.serializeNBT(provider).copy())
                .toList();
        outputSlotData = outputSlots.stream()
                .map(slot -> slot.serializeNBT(provider).copy())
                .toList();
        fluids = fluidTanks.stream().map(tank -> tank.getFluid().copy()).toList();
    }
}
