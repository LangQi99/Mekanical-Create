package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.AllBlocks;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.ExecutionPlan;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.tier.BaseTier;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismSounds;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.component.config.slot.FluidSlotInfo;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.tile.interfaces.IBoundingBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.upgrade.MachineUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimulationChamberBlockEntity extends TileEntityConfigurableMachine implements IBoundingBlock {
    public static final int INPUT_COUNT = 16;
    public static final int OUTPUT_COUNT = 4;
    public static final int INPUT_FLUID_TANK_COUNT = 2;
    public static final int OUTPUT_FLUID_TANK_COUNT = 2;
    public static final int FLUID_TANK_CAPACITY = 10 * FluidType.BUCKET_VOLUME;
    private static final long BASE_ENERGY_CAPACITY = 100_000L;
    private static final long BASE_ENERGY_USAGE = 100L;
    private static final int DEFAULT_DURATION = 100;

    private MachineEnergyContainer<SimulationChamberBlockEntity> energyContainer;
    private EnergyInventorySlot energySlot;
    private BasicInventorySlot moduleSlot;
    private BasicInventorySlot conditionSlot;
    private MultiTankFluidInventorySlot fluidContainerSlot;
    private OutputInventorySlot fluidContainerOutputSlot;
    private List<InputInventorySlot> inputSlots;
    private List<OutputInventorySlot> outputSlots;
    private List<IExtendedFluidTank> inputFluidTanks;
    private List<IExtendedFluidTank> outputFluidTanks;

    private int progress;
    private int duration = DEFAULT_DURATION;
    private int activeLaneCount;
    private int runningLaneCount;
    private boolean energyStarved;
    private boolean planDirty = true;
    private boolean schedulingNeeded = true;
    private long observedRecipeEpoch = SimulationRecipeResolver.cacheEpoch();
    private final RecipeLookupThrottle lookupThrottle = new RecipeLookupThrottle();
    private final RecipeRoundRobinState roundRobinState = new RecipeRoundRobinState();
    private final List<LaneState> lanes = new ArrayList<>();

    public SimulationChamberBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlocks.SIMULATION_CHAMBER, pos, state);
    }

    public SimulationChamberBlockEntity(Holder<Block> blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);

        configComponent.setupItemIOConfig(List.copyOf(inputSlots), List.copyOf(outputSlots), energySlot, false);
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            List<IInventorySlot> extraSlots = new ArrayList<>();
            extraSlots.add(moduleSlot);
            extraSlots.add(conditionSlot);
            if (supportsFluids()) {
                extraSlots.add(fluidContainerSlot);
            }
            itemConfig.addSlotInfo(DataType.EXTRA,
                    new InventorySlotInfo(true, true, extraSlots));
            List<IInventorySlot> configuredOutputs = new ArrayList<>(outputSlots);
            if (supportsFluids()) {
                configuredOutputs.add(fluidContainerOutputSlot);
            }
            itemConfig.addSlotInfo(DataType.OUTPUT,
                    new InventorySlotInfo(false, true, configuredOutputs));
            for (RelativeSide side : RelativeSide.values()) {
                itemConfig.setDataType(DataType.INPUT, side);
            }
            itemConfig.setDataType(DataType.OUTPUT, RelativeSide.RIGHT);
            itemConfig.setDataType(DataType.EXTRA, RelativeSide.BOTTOM);
            itemConfig.setDataType(DataType.ENERGY, RelativeSide.BACK);
        }
        ConfigInfo energyConfig = configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);
        if (energyConfig != null) {
            for (RelativeSide side : RelativeSide.values()) {
                energyConfig.setDataType(DataType.INPUT, side);
            }
        }
        ConfigInfo fluidConfig = supportsFluids()
                ? configComponent.getConfig(TransmissionType.FLUID) : null;
        if (fluidConfig != null && inputFluidTanks != null && outputFluidTanks != null) {
            fluidConfig.addSlotInfo(DataType.INPUT,
                    new FluidSlotInfo(true, false, inputFluidTanks));
            fluidConfig.addSlotInfo(DataType.OUTPUT,
                    new FluidSlotInfo(false, true, outputFluidTanks));
            for (RelativeSide side : RelativeSide.values()) {
                fluidConfig.setDataType(DataType.INPUT, side);
            }
            fluidConfig.setDataType(DataType.OUTPUT, RelativeSide.RIGHT);
            fluidConfig.setEjecting(true);
        }

        ejectorComponent = new TileComponentEjector(this);
        if (supportsFluids()) {
            ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.FLUID);
        } else {
            ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
        }
    }

    public static long getBaseEnergyCapacity() {
        return getBaseEnergyCapacity(null);
    }

    public static long getBaseEnergyUsage() {
        return getBaseEnergyUsage(null);
    }

    public static long getBaseEnergyCapacity(@Nullable BaseTier tier) {
        return BASE_ENERGY_CAPACITY * getTierMultiplier(tier);
    }

    public static long getBaseEnergyUsage(@Nullable BaseTier tier) {
        // Factory tiers add process lanes. They do not make every individual
        // operation intrinsically more expensive; running lanes are summed at
        // runtime just like Mekanism's own factories.
        return BASE_ENERGY_USAGE;
    }

    private static long getTierMultiplier(@Nullable BaseTier tier) {
        if (tier == null) {
            return 1;
        }
        return switch (tier) {
            case BASIC -> 2;
            case ADVANCED -> 4;
            case ELITE -> 8;
            case ULTIMATE, CREATIVE -> 16;
        };
    }

    @NotNull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener));
        return builder.build();
    }

    @Override
    @Nullable
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        if (!supportsFluids()) {
            return null;
        }
        inputFluidTanks = new ArrayList<>(INPUT_FLUID_TANK_COUNT);
        outputFluidTanks = new ArrayList<>(OUTPUT_FLUID_TANK_COUNT);
        IContentsListener inputListener = () -> {
            listener.onContentsChanged();
            onInputsChanged();
        };
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this);
        for (int tank = 0; tank < INPUT_FLUID_TANK_COUNT; tank++) {
            inputFluidTanks.add(builder.addTank(BasicFluidTank.input(
                    FLUID_TANK_CAPACITY, fluid -> true, inputListener)));
        }
        for (int tank = 0; tank < OUTPUT_FLUID_TANK_COUNT; tank++) {
            outputFluidTanks.add(builder.addTank(BasicFluidTank.output(
                    FLUID_TANK_CAPACITY, listener)));
        }
        return builder.build();
    }

    @NotNull
    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        inputSlots = new ArrayList<>(INPUT_COUNT);
        outputSlots = new ArrayList<>(OUTPUT_COUNT);

        IContentsListener configurationListener = () -> {
            listener.onContentsChanged();
            lookupThrottle.clear();
            invalidatePlan();
        };
        IContentsListener inputListener = () -> {
            listener.onContentsChanged();
            onInputsChanged();
        };

        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        moduleSlot = builder.addSlot(BasicInventorySlot.at(
                this::isSupportedModule, configurationListener, 25, 24, 1));
        conditionSlot = builder.addSlot(new BasicInventorySlot(1,
                (stack, automation) -> true,
                (stack, automation) -> isConditionModuleInstalled(),
                stack -> SimulationRecipeResolver.isCompatibleCondition(
                        moduleSlot.getStack(), stack),
                configurationListener, 25, 42) {
            @Override
            public InventoryContainerSlot createContainerSlot() {
                return new InventoryContainerSlot(this, 25, 42, ContainerSlotType.NORMAL,
                        null, null, this::setStackUnchecked) {
                    @Override
                    public boolean isActive() {
                        // Keep a populated slot reachable so the player can always
                        // remove its marker after changing the module.
                        return isConditionModuleInstalled() || !isEmpty();
                    }
                };
            }
        });

        for (int input = 0; input < INPUT_COUNT; input++) {
            int x = 51 + (input % 4) * 18;
            int y = 24 + (input / 4) * 18;
            inputSlots.add(builder.addSlot(InputInventorySlot.at(inputListener, x, y)));
        }
        int outputX = supportsFluids() ? 246 : 154;
        for (int output = 0; output < OUTPUT_COUNT; output++) {
            outputSlots.add(builder.addSlot(OutputInventorySlot.at(listener, outputX, 24 + output * 18)));
        }
        if (supportsFluids()) {
            fluidContainerSlot = builder.addSlot(new MultiTankFluidInventorySlot(
                    inputFluidTanks, outputFluidTanks, listener, 138, 60));
            fluidContainerSlot.setSlotOverlay(SlotOverlay.PLUS);
            fluidContainerOutputSlot = builder.addSlot(OutputInventorySlot.at(listener, 212, 60));
            fluidContainerOutputSlot.setSlotOverlay(SlotOverlay.MINUS);
        }
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(
                energyContainer, this::getLevel, listener, 6, 80));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        energySlot.fillContainerOrConvert();
        if (supportsFluids()) {
            fluidContainerSlot.handleContainer(fluidContainerOutputSlot);
        }

        Level level = getLevel();
        if (level == null || !canFunction()) {
            runningLaneCount = 0;
            energyStarved = false;
            setActive(false);
            return sendUpdatePacket;
        }

        ensureLaneCount();

        long recipeEpoch = SimulationRecipeResolver.cacheEpoch();
        if (observedRecipeEpoch != recipeEpoch) {
            observedRecipeEpoch = recipeEpoch;
            lookupThrottle.clear();
            lookupThrottle.deferUntil(level.getGameTime()
                    + Math.floorMod(getBlockPos().hashCode(), 5));
            invalidatePlan();
        }

        boolean needsReservation = planDirty || hasEmptyLane() && schedulingNeeded;
        InputReservation reservation = null;
        if (needsReservation) {
            reservation = createInputReservation();
            validateLaneReservations(reservation);
            planDirty = false;
        }

        boolean waitedForLookup = false;
        if (hasEmptyLane() && schedulingNeeded) {
            if (lookupThrottle.shouldWait(level.getGameTime())) {
                waitedForLookup = true;
                if (lookupThrottle.isExplicitWait(level.getGameTime())) {
                    SimulationRecipeResolver.recordReloadDeferral();
                } else {
                    SimulationRecipeResolver.recordDebounceDeferral();
                }
            } else {
                if (reservation == null) {
                    reservation = createInputReservation();
                    validateLaneReservations(reservation);
                }
                fillEmptyLanes(level, reservation);
                lookupThrottle.resolved();
                schedulingNeeded = false;
            }
        }

        boolean didWork = processLanes();
        updateClientLaneState();
        setActive(didWork);
        if (didWork || activeLaneCount > 0 || waitedForLookup) {
            markForSave();
        }
        return sendUpdatePacket;
    }

    private void ensureLaneCount() {
        int target = getParallelProcessCount();
        while (lanes.size() < target) {
            lanes.add(new LaneState());
            schedulingNeeded = true;
        }
        while (lanes.size() > target) {
            lanes.removeLast();
            planDirty = true;
            schedulingNeeded = true;
        }
    }

    private boolean hasEmptyLane() {
        for (LaneState lane : lanes) {
            if (lane.plan == null) {
                return true;
            }
        }
        return false;
    }

    private InputReservation createInputReservation() {
        IContentsListener ignored = () -> {
        };
        List<BasicInventorySlot> items = new ArrayList<>(inputSlots.size());
        for (IInventorySlot inputSlot : inputSlots) {
            BasicInventorySlot snapshot = BasicInventorySlot.at(ignored, 0, 0);
            snapshot.setStack(inputSlot.getStack().copy());
            items.add(snapshot);
        }
        List<IExtendedFluidTank> fluids = new ArrayList<>();
        if (supportsFluids()) {
            for (IExtendedFluidTank inputTank : inputFluidTanks) {
                BasicFluidTank snapshot = BasicFluidTank.input(
                        inputTank.getCapacity(), fluid -> true, ignored);
                snapshot.setStackUnchecked(inputTank.getFluid().copy());
                fluids.add(snapshot);
            }
        }
        return new InputReservation(items, fluids);
    }

    private void validateLaneReservations(InputReservation reservation) {
        boolean[] valid = FactoryLanePolicy.reserveInOrder(lanes,
                lane -> {
                    ExecutionPlan reserved = lane.completionPlan != null
                            ? lane.completionPlan : lane.plan;
                    return reserved == null || reservation.reserve(reserved);
                });
        for (int lane = 0; lane < lanes.size(); lane++) {
            if (!valid[lane]) {
                lanes.get(lane).clear();
            }
        }
    }

    private void fillEmptyLanes(Level level, InputReservation reservation) {
        if (isFanModuleInstalled() && lanes.stream()
                .anyMatch(lane -> lane.plan != null && lane.plan.isFanProcessing())) {
            // A fan lane is already one whole batch. Do not let factory-tier
            // parallel lanes claim a fifth output group in the same cycle.
            return;
        }
        RecipeRoundRobinState queuedRoundRobin = new RecipeRoundRobinState();
        queuedRoundRobin.restore(roundRobinState.snapshot());
        // Existing lanes have reserved their future turns, but those turns are
        // not committed to the persistent cursor until the operation actually
        // consumes input and inserts output. Include the reservations only in
        // this temporary scheduling view so newly filled lanes do not duplicate
        // an already queued equal-priority result.
        for (LaneState lane : lanes) {
            if (lane.plan != null) {
                ExecutionPlan reserved = lane.completionPlan != null
                        ? lane.completionPlan : lane.plan;
                reserved.advanceRoundRobin(queuedRoundRobin);
            }
        }
        for (LaneState lane : lanes) {
            if (lane.plan != null) {
                continue;
            }
            ExecutionPlan plan = null;
            if (lane.repeatEligible && lane.repeatPlan != null) {
                plan = lane.repeatPlan.repeat(level, reservation.items, reservation.fluids)
                        .orElse(null);
            }
            lane.repeatEligible = false;
            lane.repeatPlan = null;
            if (plan == null) {
                plan = SimulationRecipeResolver.resolve(
                        level, moduleSlot.getStack(), conditionSlot.getStack(),
                        reservation.items, reservation.fluids, supportsFluids(),
                        queuedRoundRobin).orElse(null);
            }
            if (plan == null || !reservation.reserve(plan)) {
                // Every later lane sees the same remaining pool, so another
                // full resolver pass cannot discover additional work.
                break;
            }
            lane.start(plan, adjustedDuration(plan), adjustedEnergy(plan));
            // Reserve the deterministic equal-priority result in the temporary
            // scheduling view. Later lanes therefore select the next result,
            // while the persistent cursor still waits for real completion.
            plan.advanceRoundRobin(queuedRoundRobin);
            if (plan.isFanProcessing()) {
                break;
            }
        }
    }

    private boolean processLanes() {
        Level level = getLevel();
        if (level == null) {
            return false;
        }
        OutputReservation outputReservation = new OutputReservation();
        List<LaneState> runnable = new ArrayList<>(lanes.size());
        long requestedEnergy = 0;
        long availableEnergy = energyContainer.getEnergy();
        boolean starved = false;
        for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
            LaneState lane = lanes.get(laneIndex);
            if (lane.plan == null) {
                lane.status = LaneStatus.IDLE;
                continue;
            }
            requestedEnergy = saturatingAdd(requestedEnergy, lane.energyPerTick);
            // Allocate the shared energy pool in stable lane order, but do not
            // let one expensive lane prevent a later, cheaper independent lane
            // from using energy that is actually available.
            boolean hasEnergy = lane.energyPerTick <= availableEnergy;
            boolean finishing = lane.progress + 1 >= lane.duration;
            ExecutionPlan executionPlan = lane.completionPlan != null
                    ? lane.completionPlan : lane.plan;
            if (hasEnergy && FanProcessingPolicy.shouldSnapshotAtCompletion(
                    lane.plan.isFanProcessing(), finishing,
                    lane.completionPlan != null)) {
                executionPlan = SimulationRecipeResolver.resolveFanCompletion(
                        level, moduleSlot.getStack(), conditionSlot.getStack(),
                        inputSlots, supportsFluids(), roundRobinState).orElse(null);
                if (executionPlan == null) {
                    lane.clear();
                    planDirty = true;
                    schedulingNeeded = true;
                    continue;
                }
                // Lock the completion snapshot. If outputs are blocked, the
                // same rolls and the same four groups are retried next tick.
                lane.completionPlan = executionPlan;
            }
            if (!hasEnergy) {
                lane.status = LaneStatus.ENERGY_STARVED;
                starved = true;
                continue;
            }
            if (finishing && !outputReservation.reserve(
                    executionPlan.itemResults(), executionPlan.fluidResults())) {
                lane.status = LaneStatus.OUTPUT_BLOCKED;
                continue;
            }
            lane.status = LaneStatus.RUNNING;
            runnable.add(lane);
            availableEnergy -= lane.energyPerTick;
        }
        energyContainer.setEnergyPerTick(requestedEnergy == 0
                ? adjustedIdleEnergyUsage() : requestedEnergy);

        boolean didWork = false;
        for (LaneState lane : runnable) {
            long energy = lane.energyPerTick;
            if (energyContainer.extract(energy, Action.SIMULATE,
                    AutomationType.INTERNAL) != energy) {
                lane.status = LaneStatus.ENERGY_STARVED;
                starved = true;
                continue;
            }
            energyContainer.extract(energy, Action.EXECUTE, AutomationType.INTERNAL);
            lane.progress++;
            didWork = true;
            if (lane.progress < lane.duration) {
                continue;
            }
            ExecutionPlan completed = lane.completionPlan != null
                    ? lane.completionPlan : lane.plan;
            List<? extends IExtendedFluidTank> fluids = supportsFluids()
                    ? inputFluidTanks : List.of();
            if (!completed.stillValid(inputSlots, fluids)
                    || !canFit(completed.itemResults(), completed.fluidResults())) {
                lane.clear();
                planDirty = true;
                continue;
            }
            completed.consume(inputSlots, fluids);
            insertResults(completed.itemResults());
            insertFluidResults(completed.fluidResults());
            completed.advanceRoundRobin(roundRobinState);
            lane.complete(completed);
            planDirty = true;
            lookupThrottle.clear();
        }
        energyStarved = starved;
        return didWork;
    }

    private int adjustedDuration(ExecutionPlan plan) {
        return Math.max(1, MekanismUtils.getTicks(this, plan.duration()));
    }

    private long adjustedEnergy(ExecutionPlan plan) {
        return Math.max(1, MekanismUtils.getEnergyPerTick(this, plan.energyPerTick()));
    }

    private long adjustedIdleEnergyUsage() {
        return Math.max(1, MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_USAGE));
    }

    private void updateClientLaneState() {
        activeLaneCount = 0;
        runningLaneCount = 0;
        LaneState display = null;
        double bestProgress = 0;
        for (LaneState lane : lanes) {
            if (lane.plan == null) {
                continue;
            }
            activeLaneCount++;
            if (lane.status == LaneStatus.RUNNING) {
                runningLaneCount++;
            }
            double scaled = lane.duration <= 0 ? 0 : lane.progress / (double) lane.duration;
            if (display == null || scaled > bestProgress) {
                display = lane;
                bestProgress = scaled;
            }
        }
        if (display == null) {
            progress = 0;
            duration = DEFAULT_DURATION;
            if (energyContainer.getEnergyPerTick() != adjustedIdleEnergyUsage()) {
                resetEnergyUsage();
            }
        } else {
            progress = display.progress;
            duration = display.duration;
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private void insertResults(List<ItemStack> results) {
        for (ItemStack result : results) {
            ItemStack remainder = result.copy();
            for (OutputInventorySlot outputSlot : outputSlots) {
                remainder = outputSlot.insertItem(remainder, Action.EXECUTE, AutomationType.INTERNAL);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
    }

    private boolean canFit(List<ItemStack> results, List<FluidStack> fluidResults) {
        ItemStackHandler simulation = new ItemStackHandler(OUTPUT_COUNT);
        for (int slot = 0; slot < OUTPUT_COUNT; slot++) {
            simulation.setStackInSlot(slot, outputSlots.get(slot).getStack().copy());
        }
        for (ItemStack result : results) {
            if (!ItemHandlerHelper.insertItemStacked(simulation, result.copy(), false).isEmpty()) {
                return false;
            }
        }
        if (!supportsFluids()) {
            return fluidResults.isEmpty();
        }
        List<FluidStack> simulated = outputFluidTanks.stream()
                .map(tank -> tank.getFluid().copy()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (FluidStack result : fluidResults) {
            int remaining = result.getAmount();
            for (int index = 0; index < simulated.size() && remaining > 0; index++) {
                FluidStack stored = simulated.get(index);
                if (!stored.isEmpty() && FluidStack.isSameFluidSameComponents(stored, result)) {
                    int accepted = Math.min(remaining,
                            outputFluidTanks.get(index).getCapacity() - stored.getAmount());
                    stored.grow(accepted);
                    remaining -= accepted;
                }
            }
            for (int index = 0; index < simulated.size() && remaining > 0; index++) {
                if (simulated.get(index).isEmpty()) {
                    int accepted = Math.min(remaining, outputFluidTanks.get(index).getCapacity());
                    simulated.set(index, result.copyWithAmount(accepted));
                    remaining -= accepted;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private void insertFluidResults(List<FluidStack> results) {
        if (!supportsFluids()) {
            return;
        }
        for (FluidStack result : results) {
            FluidStack remainder = result.copy();
            for (IExtendedFluidTank tank : outputFluidTanks) {
                remainder = tank.insert(remainder, Action.EXECUTE, AutomationType.INTERNAL);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
    }

    private void invalidatePlan() {
        for (LaneState lane : lanes) {
            lane.clear();
        }
        planDirty = true;
        schedulingNeeded = true;
        progress = 0;
        duration = DEFAULT_DURATION;
        activeLaneCount = 0;
        runningLaneCount = 0;
        energyStarved = false;
        resetEnergyUsage();
        if (getLevel() != null) {
            markForSave();
        }
    }

    private void onInputsChanged() {
        planDirty = true;
        schedulingNeeded = true;
        for (LaneState lane : lanes) {
            lane.repeatPlan = null;
            lane.repeatEligible = false;
        }
        Level level = getLevel();
        if (level != null) {
            lookupThrottle.inputChanged(level.getGameTime());
        }
    }

    private void resetEnergyUsage() {
        energyContainer.setEnergyPerTick(adjustedIdleEnergyUsage());
    }

    public boolean isFanModuleInstalled() {
        return moduleSlot.getStack().is(AllBlocks.ENCASED_FAN.asItem());
    }

    public boolean isConditionModuleInstalled() {
        return isFanModuleInstalled()
                || CreateSifterCompat.isSifterModule(moduleSlot.getStack());
    }

    public BasicInventorySlot getModuleSlot() {
        return moduleSlot;
    }

    public BasicInventorySlot getConditionSlot() {
        return conditionSlot;
    }

    public List<InputInventorySlot> getInputSlots() {
        return List.copyOf(inputSlots);
    }

    public List<IExtendedFluidTank> getInputFluidTanks() {
        return supportsFluids() ? List.copyOf(inputFluidTanks) : List.of();
    }

    public List<IExtendedFluidTank> getOutputFluidTanks() {
        return supportsFluids() ? List.copyOf(outputFluidTanks) : List.of();
    }

    public boolean supportsFluids() {
        return getBlockHolder() == ModBlocks.FLUID_MEKANICAL_FACTORY;
    }

    /**
     * Mekanism's bounding blocks forward automation to the controller. The fluid
     * factory deliberately exposes the same configured capabilities from every
     * part of its 3 x 2 x 3 footprint, matching other large Mekanism machines.
     */
    @Nullable
    @Override
    public <T> T getOffsetCapabilityIfEnabled(BlockCapability<T, net.minecraft.core.Direction> capability,
                                              net.minecraft.core.Direction side,
                                              net.minecraft.core.Vec3i offset) {
        Level level = getLevel();
        return level == null ? null : level.getCapability(capability, getBlockPos(), side);
    }

    public MachineEnergyContainer<SimulationChamberBlockEntity> getEnergyContainer() {
        return energyContainer;
    }

    public int getParallelProcessCount() {
        return processCountFor(Attribute.getTier(getBlockHolder(), FactoryTier.class));
    }

    static int processCountFor(@Nullable FactoryTier tier) {
        return FactoryLanePolicy.processCount(tier == null ? 0 : tier.processes);
    }

    public int getActiveLaneCount() {
        return activeLaneCount;
    }

    public int getRunningLaneCount() {
        return runningLaneCount;
    }

    public double getScaledProgress() {
        return duration <= 0 ? 0 : progress / (double) duration;
    }

    public boolean isEnergyStarved() {
        return energyStarved;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> progress, value -> progress = value));
        container.track(SyncableInt.create(() -> duration, value -> duration = value));
        container.track(SyncableInt.create(() -> activeLaneCount, value -> activeLaneCount = value));
        container.track(SyncableInt.create(() -> runningLaneCount, value -> runningLaneCount = value));
        container.track(SyncableBoolean.create(() -> energyStarved, value -> energyStarved = value));
        container.track(SyncableLong.create(energyContainer::getEnergyPerTick,
                energyContainer::setEnergyPerTick));
    }

    @Override
    public void recalculateUpgrades(Upgrade upgrade) {
        super.recalculateUpgrades(upgrade);
        if (upgrade != Upgrade.SPEED && upgrade != Upgrade.ENERGY) {
            return;
        }
        for (LaneState lane : lanes) {
            if (lane.plan == null) {
                continue;
            }
            if (upgrade == Upgrade.SPEED) {
                int oldDuration = lane.duration;
                int newDuration = adjustedDuration(lane.plan);
                lane.progress = oldDuration <= 0 ? 0 : Math.min(newDuration - 1,
                        (int) Math.floor(lane.progress * (double) newDuration / oldDuration));
                lane.duration = newDuration;
            }
            lane.energyPerTick = adjustedEnergy(lane.plan);
        }
        updateClientLaneState();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("Progress", progress);
        tag.putInt("Duration", duration);
        RecipeRoundRobinNbt.write(tag, roundRobinState);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        RecipeRoundRobinNbt.read(tag, roundRobinState);
        // Active plans are deliberately ephemeral. Re-resolve after a reload so a
        // datapack recipe change can never finish an old operation.
        progress = 0;
        duration = DEFAULT_DURATION;
        lanes.clear();
        activeLaneCount = 0;
        runningLaneCount = 0;
        energyStarved = false;
        planDirty = true;
        schedulingNeeded = true;
        lookupThrottle.clear();
        resetEnergyUsage();
    }

    @NotNull
    @Override
    public MachineUpgradeData getUpgradeData(HolderLookup.Provider provider) {
        if (!supportsFluids()) {
            List<IInventorySlot> storedInputs = new ArrayList<>(INPUT_COUNT + 2);
            storedInputs.add(moduleSlot);
            storedInputs.add(conditionSlot);
            storedInputs.addAll(inputSlots);
            return new MachineUpgradeData(provider, redstone, getControlType(), energyContainer,
                    new int[]{progress}, energySlot, storedInputs,
                    new ArrayList<>(outputSlots), false, getComponents());
        }
        List<IInventorySlot> storedInputs = new ArrayList<>(INPUT_COUNT + 3);
        storedInputs.add(moduleSlot);
        storedInputs.add(conditionSlot);
        storedInputs.addAll(inputSlots);
        storedInputs.add(fluidContainerSlot);
        List<IInventorySlot> storedOutputs = new ArrayList<>(outputSlots);
        storedOutputs.add(fluidContainerOutputSlot);
        List<IExtendedFluidTank> storedFluids = new ArrayList<>(inputFluidTanks);
        storedFluids.addAll(outputFluidTanks);
        return new SimulationChamberUpgradeData(provider, redstone, getControlType(), energyContainer,
                new int[]{progress}, energySlot, storedInputs, storedOutputs, storedFluids, getComponents());
    }

    @Override
    public void parseUpgradeData(HolderLookup.Provider provider, @NotNull IUpgradeData upgradeData) {
        if (!supportsFluids() && upgradeData instanceof MachineUpgradeData data
                && data.inputSlots.size() == INPUT_COUNT + 2
                && data.outputSlots.size() == OUTPUT_COUNT) {
            restoreCommonUpgradeData(provider, data);
            return;
        }
        if (!(upgradeData instanceof SimulationChamberUpgradeData data)
                || data.inputSlots.size() != INPUT_COUNT + 3
                || data.outputSlots.size() != OUTPUT_COUNT + 1
                || data.fluids.size() != INPUT_FLUID_TANK_COUNT + OUTPUT_FLUID_TANK_COUNT) {
            super.parseUpgradeData(provider, upgradeData);
            return;
        }
        restoreCommonUpgradeData(provider, data);
        fluidContainerSlot.deserializeNBT(provider,
                data.inputSlots.get(INPUT_COUNT + 2).serializeNBT(provider));
        fluidContainerOutputSlot.deserializeNBT(provider,
                data.outputSlots.get(OUTPUT_COUNT).serializeNBT(provider));
        List<IExtendedFluidTank> allFluidTanks = new ArrayList<>(inputFluidTanks);
        allFluidTanks.addAll(outputFluidTanks);
        for (int index = 0; index < allFluidTanks.size(); index++) {
            allFluidTanks.get(index).setStackUnchecked(data.fluids.get(index).copy());
        }
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            level.playSound(null, getBlockPos(), MekanismSounds.HYDRAULIC.get(),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    private void restoreCommonUpgradeData(HolderLookup.Provider provider, MachineUpgradeData data) {
        redstone = data.redstone;
        setControlType(data.controlType);
        energyContainer.setEnergy(data.energyContainer.getEnergy());
        energySlot.deserializeNBT(provider, data.energySlot.serializeNBT(provider));
        moduleSlot.deserializeNBT(provider, data.inputSlots.get(0).serializeNBT(provider));
        conditionSlot.deserializeNBT(provider, data.inputSlots.get(1).serializeNBT(provider));
        for (int index = 0; index < INPUT_COUNT; index++) {
            inputSlots.get(index).deserializeNBT(provider,
                    data.inputSlots.get(index + 2).serializeNBT(provider));
        }
        for (int index = 0; index < OUTPUT_COUNT; index++) {
            outputSlots.get(index).deserializeNBT(provider,
                    data.outputSlots.get(index).serializeNBT(provider));
        }
        for (ITileComponent component : getComponents()) {
            component.read(data.components, provider);
        }
        progress = 0;
        duration = DEFAULT_DURATION;
        lanes.clear();
        activeLaneCount = 0;
        runningLaneCount = 0;
        energyStarved = false;
        planDirty = true;
        schedulingNeeded = true;
        lookupThrottle.clear();
        resetEnergyUsage();
    }

    private record InputReservation(List<BasicInventorySlot> items,
                                    List<IExtendedFluidTank> fluids) {
        private boolean reserve(ExecutionPlan plan) {
            if (!plan.stillValid(items, fluids)) {
                return false;
            }
            plan.consume(items, fluids);
            return true;
        }
    }

    /**
     * Simulates all lanes that can finish during this tick. A failed lane does
     * not mutate the reservation, so a later lane producing a different item
     * can still finish instead of being stalled behind it.
     */
    private final class OutputReservation {
        private ItemStackHandler items;
        private List<FluidStack> fluids;

        private OutputReservation() {
            items = new ItemStackHandler(OUTPUT_COUNT);
            for (int slot = 0; slot < OUTPUT_COUNT; slot++) {
                items.setStackInSlot(slot, outputSlots.get(slot).getStack().copy());
            }
            fluids = supportsFluids() ? outputFluidTanks.stream()
                    .map(tank -> tank.getFluid().copy())
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
                    : List.of();
        }

        private boolean reserve(List<ItemStack> itemResults, List<FluidStack> fluidResults) {
            ItemStackHandler itemCopy = new ItemStackHandler(OUTPUT_COUNT);
            for (int slot = 0; slot < OUTPUT_COUNT; slot++) {
                itemCopy.setStackInSlot(slot, items.getStackInSlot(slot).copy());
            }
            for (ItemStack result : itemResults) {
                if (!ItemHandlerHelper.insertItemStacked(itemCopy, result.copy(), false).isEmpty()) {
                    return false;
                }
            }
            if (!supportsFluids() && !fluidResults.isEmpty()) {
                return false;
            }
            List<FluidStack> fluidCopy = fluids.stream().map(FluidStack::copy)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            for (FluidStack result : fluidResults) {
                int remaining = result.getAmount();
                for (int index = 0; index < fluidCopy.size() && remaining > 0; index++) {
                    FluidStack stored = fluidCopy.get(index);
                    if (!stored.isEmpty() && FluidStack.isSameFluidSameComponents(stored, result)) {
                        int accepted = Math.min(remaining,
                                outputFluidTanks.get(index).getCapacity() - stored.getAmount());
                        stored.grow(accepted);
                        remaining -= accepted;
                    }
                }
                for (int index = 0; index < fluidCopy.size() && remaining > 0; index++) {
                    if (fluidCopy.get(index).isEmpty()) {
                        int accepted = Math.min(remaining,
                                outputFluidTanks.get(index).getCapacity());
                        fluidCopy.set(index, result.copyWithAmount(accepted));
                        remaining -= accepted;
                    }
                }
                if (remaining > 0) {
                    return false;
                }
            }
            items = itemCopy;
            fluids = fluidCopy;
            return true;
        }
    }

    private enum LaneStatus {
        IDLE,
        RUNNING,
        OUTPUT_BLOCKED,
        ENERGY_STARVED
    }

    private static final class LaneState {
        @Nullable
        private ExecutionPlan plan;
        @Nullable
        private ExecutionPlan repeatPlan;
        @Nullable
        private ExecutionPlan completionPlan;
        private boolean repeatEligible;
        private int progress;
        private int duration = DEFAULT_DURATION;
        private long energyPerTick = BASE_ENERGY_USAGE;
        private LaneStatus status = LaneStatus.IDLE;

        private void start(ExecutionPlan plan, int duration, long energyPerTick) {
            this.plan = plan;
            this.completionPlan = null;
            this.progress = 0;
            this.duration = duration;
            this.energyPerTick = energyPerTick;
            this.status = LaneStatus.RUNNING;
        }

        private void complete(ExecutionPlan completed) {
            clearActive();
            repeatPlan = completed;
            repeatEligible = true;
        }

        private void clear() {
            clearActive();
            repeatPlan = null;
            repeatEligible = false;
        }

        private void clearActive() {
            plan = null;
            completionPlan = null;
            progress = 0;
            duration = DEFAULT_DURATION;
            energyPerTick = BASE_ENERGY_USAGE;
            status = LaneStatus.IDLE;
        }
    }

    private boolean isSupportedModule(ItemStack stack) {
        boolean common = stack.is(AllBlocks.DEPLOYER.asItem())
                || stack.is(AllBlocks.MECHANICAL_SAW.asItem())
                || stack.is(AllBlocks.MECHANICAL_PRESS.asItem())
                || stack.is(AllBlocks.MILLSTONE.asItem())
                || stack.is(AllBlocks.CRUSHING_WHEEL.asItem())
                || stack.is(AllBlocks.ENCASED_FAN.asItem())
                || stack.is(AllBlocks.MECHANICAL_CRAFTER.asItem());
        boolean builtIn = common || supportsFluids() && (stack.is(AllBlocks.MECHANICAL_MIXER.asItem())
                || stack.is(AllBlocks.SPOUT.asItem())
                || stack.is(AllBlocks.ITEM_DRAIN.asItem()));
        Level level = getLevel();
        return builtIn || level != null
                && SimulationRecipeResolver.isSupportedModule(level, stack, supportsFluids());
    }

}
