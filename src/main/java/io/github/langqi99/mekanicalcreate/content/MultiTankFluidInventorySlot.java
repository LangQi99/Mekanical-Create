package io.github.langqi99.mekanicalcreate.content;

import java.util.List;
import java.util.function.IntSupplier;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

/**
 * One container slot shared by a pair of input tanks and a pair of output
 * tanks. Filled containers are routed to an accepting input tank; empty (or
 * partially filled) containers are filled from the first compatible output
 * tank. The converted container is moved to the adjacent result slot.
 */
final class MultiTankFluidInventorySlot extends FluidInventorySlot {
    private final List<IExtendedFluidTank> inputTanks;
    private final List<IExtendedFluidTank> outputTanks;
    private final IntSupplier activeTankCount;
    private final IntSupplier xSupplier;
    private final IntSupplier ySupplier;
    private IExtendedFluidTank selectedTank;

    MultiTankFluidInventorySlot(List<IExtendedFluidTank> inputTanks,
                                List<IExtendedFluidTank> outputTanks,
                                @Nullable IContentsListener listener, int x, int y) {
        this(inputTanks, outputTanks, inputTanks::size, listener, () -> x, () -> y);
    }

    MultiTankFluidInventorySlot(List<IExtendedFluidTank> inputTanks,
                                List<IExtendedFluidTank> outputTanks,
                                IntSupplier activeTankCount,
                                @Nullable IContentsListener listener, int x, int y) {
        this(inputTanks, outputTanks, activeTankCount, listener, () -> x, () -> y);
    }

    MultiTankFluidInventorySlot(List<IExtendedFluidTank> inputTanks,
                                List<IExtendedFluidTank> outputTanks,
                                IntSupplier activeTankCount,
                                @Nullable IContentsListener listener,
                                IntSupplier xSupplier,
                                IntSupplier ySupplier) {
        super(inputTanks.getFirst(), ConstantPredicates.alwaysFalse(),
                stack -> FluidInventorySlot.tryGetFluidHandlerUnstacked(stack) != null,
                listener, 0, 0);
        this.inputTanks = List.copyOf(inputTanks);
        this.outputTanks = List.copyOf(outputTanks);
        this.activeTankCount = activeTankCount;
        this.xSupplier = xSupplier;
        this.ySupplier = ySupplier;
        selectedTank = inputTanks.getFirst();
    }

    @Override
    public InventoryContainerSlot createContainerSlot() {
        return new InventoryContainerSlot(this, xSupplier.getAsInt(), ySupplier.getAsInt(),
                getSlotType(), getSlotOverlay(), null, this::setStackUnchecked);
    }

    @Override
    public IExtendedFluidTank getFluidTank() {
        return selectedTank;
    }

    void handleContainer(IInventorySlot resultSlot) {
        if (isEmpty()) {
            return;
        }
        IFluidHandlerItem handler = FluidInventorySlot.tryGetFluidHandlerUnstacked(getStack());
        if (handler == null) {
            return;
        }

        for (int itemTank = 0; itemTank < handler.getTanks(); itemTank++) {
            FluidStack fluid = handler.getFluidInTank(itemTank);
            if (fluid.isEmpty()) {
                continue;
            }
            for (int tankIndex = 0; tankIndex < activeTankCount.getAsInt(); tankIndex++) {
                IExtendedFluidTank tank = inputTanks.get(tankIndex);
                if (tank.insert(fluid, Action.SIMULATE, AutomationType.INTERNAL).getAmount()
                        < fluid.getAmount()) {
                    selectedTank = tank;
                    fillTank(resultSlot);
                    return;
                }
            }
        }

        for (int tankIndex = 0; tankIndex < activeTankCount.getAsInt(); tankIndex++) {
            IExtendedFluidTank tank = outputTanks.get(tankIndex);
            if (tank.isEmpty()) {
                continue;
            }
            ItemStack single = getStack().copyWithCount(1);
            IFluidHandlerItem singleHandler = Capabilities.FLUID.getCapability(single);
            if (singleHandler != null
                    && singleHandler.fill(tank.getFluid().copy(), FluidAction.SIMULATE) > 0) {
                selectedTank = tank;
                drainTank(resultSlot);
                return;
            }
        }
    }
}
