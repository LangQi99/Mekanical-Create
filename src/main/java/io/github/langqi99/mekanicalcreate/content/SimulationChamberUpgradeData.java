package io.github.langqi99.mekanicalcreate.content;

import java.util.List;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import mekanism.common.upgrade.MachineUpgradeData;
import net.minecraft.nbt.CompoundTag;

/**
 * Mekanism replaces the block before asking the new tile to parse its upgrade
 * data. Keep detached NBT snapshots in addition to the standard factory data
 * so removal hooks cannot empty the old slot objects before restoration.
 */
final class SimulationChamberUpgradeData extends MachineUpgradeData {
    final FloatingLong storedEnergy;
    final CompoundTag energySlotData;
    final List<CompoundTag> inputSlotData;
    final List<CompoundTag> outputSlotData;

    SimulationChamberUpgradeData(boolean redstone, RedstoneControl controlType,
                                 IEnergyContainer energyContainer, int[] progress,
                                 EnergyInventorySlot energySlot, List<IInventorySlot> inputSlots,
                                 List<IInventorySlot> outputSlots, List<ITileComponent> components) {
        super(redstone, controlType, energyContainer, progress, energySlot,
                inputSlots, outputSlots, false, components);
        storedEnergy = energyContainer.getEnergy().copy();
        energySlotData = energySlot.serializeNBT().copy();
        inputSlotData = inputSlots.stream()
                .map(slot -> slot.serializeNBT().copy())
                .toList();
        outputSlotData = outputSlots.stream()
                .map(slot -> slot.serializeNBT().copy())
                .toList();
    }
}
