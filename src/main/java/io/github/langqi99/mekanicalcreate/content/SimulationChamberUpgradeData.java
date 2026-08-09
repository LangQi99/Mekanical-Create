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
import net.neoforged.neoforge.fluids.FluidStack;

final class SimulationChamberUpgradeData extends MachineUpgradeData {
    final List<FluidStack> fluids;

    SimulationChamberUpgradeData(HolderLookup.Provider provider, boolean redstone,
                                 RedstoneControl controlType, IEnergyContainer energyContainer,
                                 int[] progress, EnergyInventorySlot energySlot,
                                 List<IInventorySlot> inputSlots, List<IInventorySlot> outputSlots,
                                 List<IExtendedFluidTank> fluidTanks,
                                 List<ITileComponent> components) {
        super(provider, redstone, controlType, energyContainer, progress, energySlot,
                inputSlots, outputSlots, false, components);
        fluids = fluidTanks.stream().map(tank -> tank.getFluid().copy()).toList();
    }
}
