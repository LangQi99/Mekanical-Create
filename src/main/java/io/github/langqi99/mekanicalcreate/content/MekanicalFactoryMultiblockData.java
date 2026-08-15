package io.github.langqi99.mekanicalcreate.content;

import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.ExecutionPlan;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.functions.ConstantPredicates;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.container.sync.dynamic.ContainerSync;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Shared processing inventory for a formed variable-size fluid factory. */
public final class MekanicalFactoryMultiblockData extends MultiblockData {
    public static final int INPUT_COUNT = 16;
    public static final int OUTPUT_COUNT = 4;
    public static final int MAX_SPEED_CORES = 4;
    public static final int MAX_ENERGY_CORES = 4;
    public static final int MAX_FLUID_CORES = 2;
    public static final int MAX_FLUID_TANK_COUNT = 3;
    public static final int MAX_CATALYST_CORES = 3;
    public static final int BASE_CATALYST_SLOT_COUNT = 2;
    public static final int CATALYST_SLOTS_PER_CORE = 2;
    public static final int MAX_CATALYST_SLOT_COUNT = 8;
    public static final int BASE_FLUID_TANK_CAPACITY = 10 * FluidType.BUCKET_VOLUME;
    public static final int FLUID_CAPACITY_PER_CORE = 5 * FluidType.BUCKET_VOLUME;
    public static final long BASE_ENERGY_CAPACITY = 100_000L;
    public static final long ENERGY_CAPACITY_PER_CORE = 100_000L;
    public static final long BASE_ENERGY_PER_TICK = 100L;
    private static final int DEFAULT_DURATION = 100;
    private static final int HIDDEN_CONTAINER_SLOT_COORDINATE = -10_000;

    private final BasicEnergyContainer energyContainer;
    private final EnergyInventorySlot energySlot;
    private final List<BasicInventorySlot> catalystSlots = new ArrayList<>(MAX_CATALYST_SLOT_COUNT);
    private final MultiTankFluidInventorySlot fluidContainerSlot;
    private final BasicInventorySlot fluidContainerOutputSlot;
    private final List<InputInventorySlot> inputSlots = new ArrayList<>(INPUT_COUNT);
    private final List<BasicInventorySlot> outputSlots = new ArrayList<>(OUTPUT_COUNT);
    private final List<IExtendedFluidTank> inputFluidTanks = new ArrayList<>(MAX_FLUID_TANK_COUNT);
    private final List<IExtendedFluidTank> outputFluidTanks = new ArrayList<>(MAX_FLUID_TANK_COUNT);

    @ContainerSync(getter = "getEnergy")
    private long clientEnergy;
    @ContainerSync(getter = "getEnergyCapacity")
    private long clientEnergyCapacity = BASE_ENERGY_CAPACITY;
    @ContainerSync(getter = "getEnergyPerTick")
    private long clientEnergyPerTick = BASE_ENERGY_PER_TICK;
    @ContainerSync
    private final BasicFluidTank inputFluidTank0;
    @ContainerSync
    private final BasicFluidTank inputFluidTank1;
    @ContainerSync
    private final BasicFluidTank inputFluidTank2;
    @ContainerSync
    private final BasicFluidTank outputFluidTank0;
    @ContainerSync
    private final BasicFluidTank outputFluidTank1;
    @ContainerSync
    private final BasicFluidTank outputFluidTank2;

    @ContainerSync
    private int progress;
    @ContainerSync
    private int duration = DEFAULT_DURATION;
    @ContainerSync
    private boolean active;
    @ContainerSync
    private boolean energyStarved;
    @ContainerSync
    private int speedCoreCount;
    @ContainerSync
    private int energyCoreCount;
    @ContainerSync
    private int fluidCoreCount;
    @ContainerSync
    private int catalystCoreCount;

    private boolean planDirty = true;
    private long observedRecipeEpoch = SimulationRecipeResolver.cacheEpoch();
    private final RecipeLookupThrottle lookupThrottle = new RecipeLookupThrottle();
    private final RecipeRoundRobinState fallbackRoundRobinState = new RecipeRoundRobinState();
    private boolean repeatEligible;
    @Nullable
    private ExecutionPlan activePlan;
    @Nullable
    private ExecutionPlan repeatPlan;

    public MekanicalFactoryMultiblockData(MekanicalFactoryBlockEntity tile) {
        super(tile);

        IContentsListener configurationListener = () -> {
            markDirty();
            lookupThrottle.clear();
            invalidatePlan();
        };
        IContentsListener inputListener = () -> {
            markDirty();
            onInputsChanged();
        };

        energyContainers.add(energyContainer = new FactoryEnergyContainer());
        inputFluidTanks.add(inputFluidTank0 = new FactoryFluidTank(false, inputListener));
        inputFluidTanks.add(inputFluidTank1 = new FactoryFluidTank(false, inputListener));
        inputFluidTanks.add(inputFluidTank2 = new FactoryFluidTank(false, inputListener));
        outputFluidTanks.add(outputFluidTank0 = new FactoryFluidTank(true, this));
        outputFluidTanks.add(outputFluidTank1 = new FactoryFluidTank(true, this));
        outputFluidTanks.add(outputFluidTank2 = new FactoryFluidTank(true, this));
        // Keep the first four cache indices compatible with the earlier two-tank
        // factory layout: input 0, input 1, output 0, output 1. The newly added
        // third input/output channels are appended afterwards.
        fluidTanks.add(inputFluidTanks.get(0));
        fluidTanks.add(inputFluidTanks.get(1));
        fluidTanks.add(outputFluidTanks.get(0));
        fluidTanks.add(outputFluidTanks.get(1));
        fluidTanks.add(inputFluidTanks.get(2));
        fluidTanks.add(outputFluidTanks.get(2));

        for (int catalyst = 0; catalyst < MAX_CATALYST_SLOT_COUNT; catalyst++) {
            int catalystIndex = catalyst;
            BasicInventorySlot slot = new BasicInventorySlot(1,
                    ConstantPredicates.alwaysTrueBi(),
                    (stack, automation) -> catalystIndex < getCatalystSlotCount(),
                    ConstantPredicates.alwaysTrue(), configurationListener,
                    MekanicalFactoryGuiLayout.catalystX(catalyst),
                    MekanicalFactoryGuiLayout.catalystY(catalyst)) {
                @Override
                public InventoryContainerSlot createContainerSlot() {
                    // GuiMekanism draws a background for every container slot,
                    // even when Slot#isActive returns false. Keep locked catalyst
                    // slots outside the GUI so only unlocked slots are rendered.
                    boolean visible = catalystIndex < getCatalystSlotCount()
                            || !getStack().isEmpty();
                    int slotX = visible
                            ? MekanicalFactoryGuiLayout.catalystX(catalystIndex)
                            : HIDDEN_CONTAINER_SLOT_COORDINATE;
                    int slotY = visible
                            ? MekanicalFactoryGuiLayout.catalystY(catalystIndex)
                            : HIDDEN_CONTAINER_SLOT_COORDINATE;
                    return new InventoryContainerSlot(this,
                            slotX, slotY,
                            ContainerSlotType.NORMAL, null, null,
                            this::setStackUnchecked) {
                        @Override
                        public boolean isActive() {
                            return catalystIndex < getCatalystSlotCount() || hasItem();
                        }
                    };
                }
            };
            catalystSlots.add(slot);
            // Preserve the two legacy module/condition cache indices.
            if (catalyst < BASE_CATALYST_SLOT_COUNT) {
                inventorySlots.add(slot);
            }
        }
        for (int input = 0; input < INPUT_COUNT; input++) {
            int inputIndex = input;
            InputInventorySlot slot = new DynamicInputInventorySlot(inputListener,
                    () -> MekanicalFactoryGuiLayout.inputX(
                            getCatalystSlotCount(), inputIndex),
                    () -> MekanicalFactoryGuiLayout.inputY(inputIndex));
            inputSlots.add(slot);
            inventorySlots.add(slot);
        }
        for (int output = 0; output < OUTPUT_COUNT; output++) {
            int outputIndex = output;
            BasicInventorySlot slot = new DynamicOutputInventorySlot(this,
                    () -> MekanicalFactoryGuiLayout.outputSlotX(
                            getFluidTankCount(), getCatalystSlotCount(), outputIndex),
                    () -> MekanicalFactoryGuiLayout.outputSlotY(outputIndex));
            outputSlots.add(slot);
            inventorySlots.add(slot);
        }
        inventorySlots.add(fluidContainerSlot = new MultiTankFluidInventorySlot(
                inputFluidTanks, outputFluidTanks, this::getFluidTankCount,
                this,
                () -> MekanicalFactoryGuiLayout.inputFluidContainerX(
                        getFluidTankCount(), getCatalystSlotCount()),
                () -> MekanicalFactoryGuiLayout.FLUID_CONTAINER_Y));
        fluidContainerSlot.setSlotOverlay(SlotOverlay.PLUS);
        inventorySlots.add(fluidContainerOutputSlot = new DynamicOutputInventorySlot(this,
                () -> MekanicalFactoryGuiLayout.outputFluidContainerX(
                        getFluidTankCount(), getCatalystSlotCount()),
                () -> MekanicalFactoryGuiLayout.FLUID_CONTAINER_Y));
        fluidContainerOutputSlot.setSlotOverlay(SlotOverlay.MINUS);
        inventorySlots.add(energySlot = EnergyInventorySlot.fillOrConvert(
                energyContainer, tile::getLevel, this,
                MekanicalFactoryGuiLayout.ENERGY_SLOT_X,
                MekanicalFactoryGuiLayout.ENERGY_SLOT_Y));
        // Additional catalyst slots are appended so existing worlds retain all
        // historical inventory cache indices for inputs, outputs and containers.
        for (int catalyst = BASE_CATALYST_SLOT_COUNT;
             catalyst < MAX_CATALYST_SLOT_COUNT; catalyst++) {
            inventorySlots.add(catalystSlots.get(catalyst));
        }
    }

    @Override
    public boolean tick(Level level) {
        boolean needsPacket = super.tick(level);
        energySlot.fillContainerOrConvert();
        fluidContainerSlot.handleContainer(fluidContainerOutputSlot);

        long recipeEpoch = SimulationRecipeResolver.cacheEpoch();
        if (observedRecipeEpoch != recipeEpoch) {
            observedRecipeEpoch = recipeEpoch;
            lookupThrottle.clear();
            lookupThrottle.deferUntil(level.getGameTime()
                    + Math.floorMod(getMinPos().hashCode(), 5));
            invalidatePlan();
        }

        if (activePlan != null && planDirty) {
            if (!activePlan.stillValid(inputSlots, activeInputFluidTanks())) {
                invalidatePlan();
            } else {
                planDirty = false;
            }
        }
        if (activePlan == null) {
            if (!planDirty) {
                return setIdle(needsPacket);
            }
            if (lookupThrottle.shouldWait(level.getGameTime())) {
                if (lookupThrottle.isExplicitWait(level.getGameTime())) {
                    SimulationRecipeResolver.recordReloadDeferral();
                } else {
                    SimulationRecipeResolver.recordDebounceDeferral();
                }
                return setIdle(needsPacket);
            }
            if (repeatEligible && repeatPlan != null) {
                activePlan = repeatPlan.repeat(level, inputSlots, activeInputFluidTanks())
                        .orElse(null);
            }
            repeatEligible = false;
            repeatPlan = null;
            if (activePlan == null) {
                activePlan = SimulationRecipeResolver.resolve(level, activeCatalystSlots(),
                        inputSlots, activeInputFluidTanks(), true,
                        roundRobinState()).orElse(null);
            }
            planDirty = false;
            lookupThrottle.resolved();
            if (activePlan == null) {
                return setIdle(needsPacket);
            }
            progress = 0;
            duration = getAdjustedDuration(activePlan.duration());
        }
        if (!canFit(activePlan.itemResults(), activePlan.fluidResults())) {
            return setInactive(needsPacket, false);
        }
        long energyPerTick = getEnergyPerTick();
        if (energyContainer.extract(energyPerTick, Action.SIMULATE,
                AutomationType.INTERNAL) != energyPerTick) {
            return setInactive(needsPacket, true);
        }

        energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
        needsPacket |= !active || energyStarved;
        active = true;
        energyStarved = false;
        progress++;
        if (progress >= duration) {
            if (!activePlan.stillValid(inputSlots, activeInputFluidTanks())
                    || !canFit(activePlan.itemResults(), activePlan.fluidResults())) {
                invalidatePlan();
                return setInactive(true, false);
            }
            ExecutionPlan completedPlan = activePlan;
            activePlan.consume(inputSlots, activeInputFluidTanks());
            insertResults(activePlan.itemResults());
            insertFluidResults(activePlan.fluidResults());
            advanceRoundRobin(activePlan);
            activePlan = null;
            progress = 0;
            duration = DEFAULT_DURATION;
            planDirty = true;
            repeatPlan = completedPlan;
            repeatEligible = true;
            lookupThrottle.clear();
            markDirty();
            needsPacket = true;
        }
        return needsPacket;
    }

    private boolean setIdle(boolean needsPacket) {
        if (progress != 0 || duration != DEFAULT_DURATION || active || energyStarved) {
            progress = 0;
            duration = DEFAULT_DURATION;
            active = false;
            energyStarved = false;
            return true;
        }
        return needsPacket;
    }

    private boolean setInactive(boolean needsPacket, boolean starved) {
        if (active || energyStarved != starved) {
            active = false;
            energyStarved = starved;
            return true;
        }
        return needsPacket;
    }

    private void invalidatePlan() {
        activePlan = null;
        repeatPlan = null;
        repeatEligible = false;
        planDirty = true;
        progress = 0;
        duration = DEFAULT_DURATION;
    }

    private void onInputsChanged() {
        planDirty = true;
        repeatPlan = null;
        repeatEligible = false;
        Level level = getLevel();
        if (level != null) {
            lookupThrottle.inputChanged(level.getGameTime());
        }
    }

    private void insertResults(List<ItemStack> results) {
        for (ItemStack result : results) {
            ItemStack remainder = result.copy();
            for (BasicInventorySlot outputSlot : outputSlots) {
                remainder = outputSlot.insertItem(remainder, Action.EXECUTE, AutomationType.INTERNAL);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
    }

    private void insertFluidResults(List<FluidStack> results) {
        for (FluidStack result : results) {
            FluidStack remainder = result.copy();
            for (IExtendedFluidTank tank : activeOutputFluidTanks()) {
                remainder = tank.insert(remainder, Action.EXECUTE, AutomationType.INTERNAL);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
    }

    private boolean canFit(List<ItemStack> results, List<FluidStack> fluidResults) {
        ItemStackHandler itemSimulation = new ItemStackHandler(OUTPUT_COUNT);
        for (int slot = 0; slot < OUTPUT_COUNT; slot++) {
            itemSimulation.setStackInSlot(slot, outputSlots.get(slot).getStack().copy());
        }
        for (ItemStack result : results) {
            if (!ItemHandlerHelper.insertItemStacked(itemSimulation, result.copy(), false).isEmpty()) {
                return false;
            }
        }

        List<IExtendedFluidTank> activeOutputTanks = activeOutputFluidTanks();
        List<FluidStack> fluidSimulation = activeOutputTanks.stream()
                .map(tank -> tank.getFluid().copy())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (FluidStack result : fluidResults) {
            int remaining = result.getAmount();
            for (int index = 0; index < fluidSimulation.size() && remaining > 0; index++) {
                FluidStack stored = fluidSimulation.get(index);
                if (!stored.isEmpty() && FluidStack.isSameFluidSameComponents(stored, result)) {
                    int accepted = Math.min(remaining,
                            activeOutputTanks.get(index).getCapacity() - stored.getAmount());
                    stored.grow(accepted);
                    remaining -= accepted;
                }
            }
            for (int index = 0; index < fluidSimulation.size() && remaining > 0; index++) {
                if (fluidSimulation.get(index).isEmpty()) {
                    int accepted = Math.min(remaining, activeOutputTanks.get(index).getCapacity());
                    fluidSimulation.set(index, result.copyWithAmount(accepted));
                    remaining -= accepted;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    public IEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    public long getEnergy() {
        return isRemote() ? clientEnergy : energyContainer.getEnergy();
    }

    public long getEnergyCapacity() {
        return isRemote()
                ? clientEnergyCapacity
                : BASE_ENERGY_CAPACITY + energyCoreCount * ENERGY_CAPACITY_PER_CORE;
    }

    public long getEnergyPerTick() {
        if (isRemote()) {
            return clientEnergyPerTick;
        }
        long base = activePlan == null
                ? BASE_ENERGY_PER_TICK
                : activePlan.energyPerTick();
        return Math.max(1L, (long) Math.ceil(
                base * (4D + getSpeedCoreCount()) / 4D));
    }

    public List<InputInventorySlot> getInputSlots() {
        return List.copyOf(inputSlots);
    }

    public List<IInventorySlot> getCatalystSlots() {
        return new ArrayList<>(catalystSlots);
    }

    public List<IInventorySlot> getCatalystSlotsForPort() {
        return new ArrayList<>(activeCatalystSlots());
    }

    public List<IInventorySlot> getInputSlotsForPort() {
        return new ArrayList<>(inputSlots);
    }

    public List<IInventorySlot> getOutputSlotsForPort() {
        return new ArrayList<>(outputSlots);
    }

    public List<IExtendedFluidTank> getInputFluidTanks() {
        return List.copyOf(activeInputFluidTanks());
    }

    public List<IExtendedFluidTank> getAllInputFluidTanks() {
        return List.copyOf(inputFluidTanks);
    }

    public List<IExtendedFluidTank> getOutputFluidTanks() {
        return List.copyOf(activeOutputFluidTanks());
    }

    public List<IExtendedFluidTank> getAllOutputFluidTanks() {
        return List.copyOf(outputFluidTanks);
    }

    public List<IExtendedFluidTank> getActiveFluidTanks() {
        List<IExtendedFluidTank> tanks = new ArrayList<>(getFluidTankCount() * 2);
        tanks.addAll(activeInputFluidTanks());
        tanks.addAll(activeOutputFluidTanks());
        return tanks;
    }

    public int getFluidTankCount() {
        return Math.min(MAX_FLUID_TANK_COUNT, 1 + getFluidCoreCount());
    }

    public int getFluidTankCapacity() {
        return BASE_FLUID_TANK_CAPACITY
                + getFluidCoreCount() * FLUID_CAPACITY_PER_CORE;
    }

    public int getSpeedCoreCount() {
        return getClientStructureCoreCount(ModBlocks.MEKANICAL_FACTORY_SPEED_CORE.get(),
                speedCoreCount, MAX_SPEED_CORES);
    }

    public int getEnergyCoreCount() {
        return getClientStructureCoreCount(ModBlocks.MEKANICAL_FACTORY_ENERGY_CORE.get(),
                energyCoreCount, MAX_ENERGY_CORES);
    }

    public int getFluidCoreCount() {
        return getClientStructureCoreCount(ModBlocks.MEKANICAL_FACTORY_FLUID_CORE.get(),
                fluidCoreCount, MAX_FLUID_CORES);
    }

    public int getCatalystCoreCount() {
        return getClientStructureCoreCount(ModBlocks.MEKANICAL_FACTORY_CATALYST_CORE.get(),
                catalystCoreCount, MAX_CATALYST_CORES);
    }

    public int getCatalystSlotCount() {
        return Math.min(MAX_CATALYST_SLOT_COUNT,
                BASE_CATALYST_SLOT_COUNT
                        + getCatalystCoreCount() * CATALYST_SLOTS_PER_CORE);
    }

    /**
     * Container data reaches the client one tick after the menu is created. For
     * that first frame, derive upgrade counts from the loaded structure itself
     * so a previously viewed factory cannot leak its layout into this one.
     */
    private int getClientStructureCoreCount(Block core, int fallback, int maximum) {
        if (!isRemote() || getLevel() == null || getBounds() == null) {
            return fallback;
        }
        BlockPos min = getMinPos();
        BlockPos max = getMaxPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int x = min.getX() + 1; x < max.getX(); x++) {
            for (int y = min.getY() + 1; y < max.getY(); y++) {
                for (int z = min.getZ() + 1; z < max.getZ(); z++) {
                    if (getLevel().getBlockState(cursor.set(x, y, z)).is(core)
                            && ++count >= maximum) {
                        return maximum;
                    }
                }
            }
        }
        return count;
    }

    void setUpgradeCoreCounts(int speedCores, int energyCores, int fluidCores,
                              int catalystCores) {
        speedCoreCount = Math.min(MAX_SPEED_CORES, Math.max(0, speedCores));
        energyCoreCount = Math.min(MAX_ENERGY_CORES, Math.max(0, energyCores));
        fluidCoreCount = Math.min(MAX_FLUID_CORES, Math.max(0, fluidCores));
        catalystCoreCount = Math.min(MAX_CATALYST_CORES, Math.max(0, catalystCores));
    }

    public double getScaledProgress() {
        return duration <= 0 ? 0 : progress / (double) duration;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isEnergyStarved() {
        return energyStarved;
    }

    private int getAdjustedDuration(int recipeDuration) {
        return Math.max(1, (int) Math.ceil(recipeDuration * 4D / (4D + speedCoreCount)));
    }

    private List<IExtendedFluidTank> activeInputFluidTanks() {
        return inputFluidTanks.subList(0, getFluidTankCount());
    }

    private List<IExtendedFluidTank> activeOutputFluidTanks() {
        return outputFluidTanks.subList(0, getFluidTankCount());
    }

    private List<BasicInventorySlot> activeCatalystSlots() {
        return catalystSlots.subList(0, getCatalystSlotCount());
    }

    private RecipeRoundRobinState roundRobinState() {
        MekanicalFactoryControllerBlockEntity controller = roundRobinController();
        return controller == null
                ? fallbackRoundRobinState : controller.getRecipeRoundRobinState();
    }

    private void advanceRoundRobin(ExecutionPlan plan) {
        MekanicalFactoryControllerBlockEntity controller = roundRobinController();
        RecipeRoundRobinState state = controller == null
                ? fallbackRoundRobinState : controller.getRecipeRoundRobinState();
        plan.advanceRoundRobin(state);
        if (controller != null) {
            controller.roundRobinChanged();
        }
    }

    @Nullable
    private MekanicalFactoryControllerBlockEntity roundRobinController() {
        Level level = getLevel();
        if (level != null) {
            for (BlockPos location : locations) {
                if (level.getBlockEntity(location)
                        instanceof MekanicalFactoryControllerBlockEntity controller) {
                    return controller;
                }
            }
        }
        return null;
    }

    private static final class DynamicOutputInventorySlot extends BasicInventorySlot {
        private final IntSupplier xSupplier;
        private final IntSupplier ySupplier;

        private DynamicOutputInventorySlot(IContentsListener listener,
                                           IntSupplier xSupplier,
                                           IntSupplier ySupplier) {
            super(ConstantPredicates.alwaysTrueBi(), ConstantPredicates.internalOnly(),
                    ConstantPredicates.alwaysTrue(), listener, 0, 0);
            this.xSupplier = xSupplier;
            this.ySupplier = ySupplier;
            setSlotType(ContainerSlotType.OUTPUT);
        }

        @Override
        public InventoryContainerSlot createContainerSlot() {
            return new InventoryContainerSlot(this, xSupplier.getAsInt(), ySupplier.getAsInt(),
                    getSlotType(), getSlotOverlay(), null, this::setStackUnchecked);
        }
    }

    private static final class DynamicInputInventorySlot extends InputInventorySlot {
        private final IntSupplier xSupplier;
        private final IntSupplier ySupplier;

        private DynamicInputInventorySlot(IContentsListener listener,
                                          IntSupplier xSupplier,
                                          IntSupplier ySupplier) {
            super(ConstantPredicates.alwaysTrue(), ConstantPredicates.alwaysTrue(),
                    listener, 0, 0);
            this.xSupplier = xSupplier;
            this.ySupplier = ySupplier;
        }

        @Override
        public InventoryContainerSlot createContainerSlot() {
            return new InventoryContainerSlot(this, xSupplier.getAsInt(), ySupplier.getAsInt(),
                    getSlotType(), getSlotOverlay(), null, this::setStackUnchecked);
        }
    }

    private final class FactoryEnergyContainer extends BasicEnergyContainer {
        private FactoryEnergyContainer() {
            super(Long.MAX_VALUE, BasicEnergyContainer.notExternal,
                    ConstantPredicates.alwaysTrue(), MekanicalFactoryMultiblockData.this);
        }

        @Override
        public long getEnergy() {
            return MekanicalFactoryMultiblockData.this.isRemote()
                    ? clientEnergy
                    : super.getEnergy();
        }

        @Override
        public long getMaxEnergy() {
            return getEnergyCapacity();
        }
    }

    private final class FactoryFluidTank extends BasicFluidTank {
        private FactoryFluidTank(boolean output, IContentsListener listener) {
            super(Integer.MAX_VALUE,
                    (stack, automation) -> output || automation != AutomationType.EXTERNAL,
                    (stack, automation) -> !output || automation != AutomationType.EXTERNAL,
                    stack -> true, listener);
        }

        @Override
        public int getCapacity() {
            return getFluidTankCapacity();
        }
    }
}
