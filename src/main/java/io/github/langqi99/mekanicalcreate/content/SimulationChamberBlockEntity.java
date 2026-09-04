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
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.api.providers.IBlockProvider;
import mekanism.api.tier.BaseTier;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableFloatingLong;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismSounds;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.util.MekanismUtils;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.upgrade.MachineUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimulationChamberBlockEntity extends TileEntityConfigurableMachine {
    public static final int INPUT_COUNT = 16;
    public static final int OUTPUT_COUNT = 4;
    private static final long BASE_ENERGY_CAPACITY = 100_000L;
    private static final long BASE_ENERGY_USAGE = 100L;
    private static final int DEFAULT_DURATION = 100;

    private MachineEnergyContainer<SimulationChamberBlockEntity> energyContainer;
    private EnergyInventorySlot energySlot;
    private BasicInventorySlot moduleSlot;
    private BasicInventorySlot conditionSlot;
    private List<InputInventorySlot> inputSlots;
    private List<OutputInventorySlot> outputSlots;

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

    public SimulationChamberBlockEntity(IBlockProvider blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);

        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY);
        configComponent.setupItemIOConfig(List.copyOf(inputSlots), List.copyOf(outputSlots), energySlot, false);
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(DataType.EXTRA,
                    new InventorySlotInfo(true, true, List.of(moduleSlot, conditionSlot)));
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

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    public static FloatingLong getBaseEnergyCapacity() {
        return getBaseEnergyCapacity(null);
    }

    public static FloatingLong getBaseEnergyUsage() {
        return getBaseEnergyUsage(null);
    }

    public static FloatingLong getBaseEnergyCapacity(@Nullable BaseTier tier) {
        return FloatingLong.create(BASE_ENERGY_CAPACITY * getTierMultiplier(tier));
    }

    public static FloatingLong getBaseEnergyUsage(@Nullable BaseTier tier) {
        // Factory tiers add process lanes. They do not make every individual
        // operation intrinsically more expensive; running lanes are summed at
        // runtime just like Mekanism's own factories.
        return FloatingLong.create(BASE_ENERGY_USAGE);
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
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this::getDirection, this::getConfig);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener));
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

        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this::getDirection, this::getConfig);
        moduleSlot = builder.addSlot(new BasicInventorySlot(1,
                BasicInventorySlot.alwaysTrueBi,
                BasicInventorySlot.alwaysTrueBi,
                this::isSupportedModule,
                configurationListener, 25, 24) {
        });
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
        for (int output = 0; output < OUTPUT_COUNT; output++) {
            outputSlots.add(builder.addSlot(OutputInventorySlot.at(listener, 154, 24 + output * 18)));
        }
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(
                energyContainer, this::getLevel, listener, 6, 80));
        return builder.build();
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        energySlot.fillContainerOrConvert();

        Level level = getLevel();
        if (level == null || !MekanismUtils.canFunction(this)) {
            runningLaneCount = 0;
            energyStarved = false;
            setActive(false);
            return;
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
    }

    private void ensureLaneCount() {
        int target = getParallelProcessCount();
        while (lanes.size() < target) {
            lanes.add(new LaneState());
            schedulingNeeded = true;
        }
        while (lanes.size() > target) {
            lanes.remove(lanes.size() - 1);
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
        return new InputReservation(items);
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
                plan = lane.repeatPlan.repeat(level, reservation.items, List.of())
                        .orElse(null);
            }
            lane.repeatEligible = false;
            lane.repeatPlan = null;
            if (plan == null) {
                plan = SimulationRecipeResolver.resolve(
                        level, moduleSlot.getStack(), conditionSlot.getStack(),
                        reservation.items, List.of(), false,
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
        FloatingLong requestedEnergy = FloatingLong.ZERO;
        FloatingLong availableEnergy = energyContainer.getEnergy();
        boolean starved = false;
        for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
            LaneState lane = lanes.get(laneIndex);
            if (lane.plan == null) {
                lane.status = LaneStatus.IDLE;
                continue;
            }
            // Allocate the shared energy pool in stable lane order, but do not
            // let one expensive lane prevent a later, cheaper independent lane
            // from using energy that is actually available.
            boolean hasEnergy = !availableEnergy.smallerThan(lane.energyPerTick);
            boolean finishing = lane.progress + 1 >= lane.duration;
            ExecutionPlan executionPlan = lane.completionPlan != null
                    ? lane.completionPlan : lane.plan;
            if (hasEnergy && FanProcessingPolicy.shouldSnapshotAtCompletion(
                    lane.plan.isFanProcessing(), finishing,
                    lane.completionPlan != null)) {
                executionPlan = SimulationRecipeResolver.resolveFanCompletion(
                        level, moduleSlot.getStack(), conditionSlot.getStack(),
                        inputSlots, outputReservation.itemStacks(), false,
                        roundRobinState).orElse(null);
                if (executionPlan == null) {
                    lane.clear();
                    planDirty = true;
                    schedulingNeeded = true;
                    continue;
                }
                // Lock the completion snapshot. If outputs are blocked, the
                // same rolls and capacity-bounded input amount are retried.
                lane.completionPlan = executionPlan;
            }
            if (!hasEnergy) {
                requestedEnergy = requestedEnergy.add(lane.energyPerTick);
                lane.status = LaneStatus.ENERGY_STARVED;
                starved = true;
                continue;
            }
            if (FanProcessingPolicy.shouldReserveOutputsBeforeProgress(
                    lane.plan.isFanProcessing(), finishing)
                    && !outputReservation.reserve(executionPlan.itemResults())) {
                lane.progress = FanProcessingPolicy.progressAfterOutputBlock(
                        lane.plan.isFanProcessing(), lane.progress);
                lane.status = LaneStatus.OUTPUT_BLOCKED;
                continue;
            }
            requestedEnergy = requestedEnergy.add(lane.energyPerTick);
            lane.status = LaneStatus.RUNNING;
            runnable.add(lane);
            availableEnergy = availableEnergy.subtract(lane.energyPerTick);
        }
        energyContainer.setEnergyPerTick(requestedEnergy.isZero()
                ? adjustedIdleEnergyUsage() : requestedEnergy);

        boolean didWork = false;
        for (LaneState lane : runnable) {
            FloatingLong energy = lane.energyPerTick;
            if (energyContainer.extract(energy, Action.SIMULATE,
                    AutomationType.INTERNAL).smallerThan(energy)) {
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
            if (!completed.stillValid(inputSlots, List.of())
                    || !canFit(completed.itemResults())) {
                lane.clear();
                planDirty = true;
                continue;
            }
            completed.consume(inputSlots, List.of());
            insertResults(completed.itemResults());
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

    private FloatingLong adjustedEnergy(ExecutionPlan plan) {
        FloatingLong adjusted = MekanismUtils.getEnergyPerTick(this,
                FloatingLong.create(Math.max(1, plan.energyPerTick())));
        return adjusted.isZero() ? FloatingLong.ONE : adjusted;
    }

    private FloatingLong adjustedIdleEnergyUsage() {
        FloatingLong adjusted = MekanismUtils.getEnergyPerTick(this,
                FloatingLong.create(BASE_ENERGY_USAGE));
        return adjusted.isZero() ? FloatingLong.ONE : adjusted;
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
            if (!energyContainer.getEnergyPerTick().equals(adjustedIdleEnergyUsage())) {
                resetEnergyUsage();
            }
        } else {
            progress = display.progress;
            duration = display.duration;
        }
    }

    private void insertResults(List<ItemStack> results) {
        for (ItemStack result : results) {
            ItemStack remainder = StackedOutputInsertion.insert(
                    outputSlots, result.copy(), ItemStack::isEmpty,
                    (slot, stack) -> ItemStack.isSameItemSameTags(
                            slot.getStack(), stack),
                    slot -> slot.getStack().isEmpty(),
                    (slot, stack) -> slot.insertItem(
                            stack, Action.EXECUTE, AutomationType.INTERNAL));
            if (!remainder.isEmpty()) {
                throw new IllegalStateException(
                        "Output capacity changed after successful reservation");
            }
        }
    }

    private boolean canFit(List<ItemStack> results) {
        ItemStackHandler simulation = new ItemStackHandler(OUTPUT_COUNT);
        for (int slot = 0; slot < OUTPUT_COUNT; slot++) {
            simulation.setStackInSlot(slot, outputSlots.get(slot).getStack().copy());
        }
        for (ItemStack result : results) {
            if (!ItemHandlerHelper.insertItemStacked(simulation, result.copy(), false).isEmpty()) {
                return false;
            }
        }
        return true;
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

    public MachineEnergyContainer<SimulationChamberBlockEntity> getEnergyContainer() {
        return energyContainer;
    }

    public int getParallelProcessCount() {
        return processCountFor(Attribute.getTier(getBlockType(), FactoryTier.class));
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
        container.track(SyncableFloatingLong.create(energyContainer::getEnergyPerTick,
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
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Progress", progress);
        tag.putInt("Duration", duration);
        RecipeRoundRobinNbt.write(tag, roundRobinState);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
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
    public MachineUpgradeData getUpgradeData() {
        List<IInventorySlot> storedInputs = new ArrayList<>(INPUT_COUNT + 2);
        storedInputs.add(moduleSlot);
        storedInputs.add(conditionSlot);
        storedInputs.addAll(inputSlots);
        List<IInventorySlot> storedOutputs = new ArrayList<>(outputSlots);
        return new SimulationChamberUpgradeData(redstone, getControlType(), energyContainer,
                new int[]{progress}, energySlot, storedInputs, storedOutputs, getComponents());
    }

    @Override
    public void parseUpgradeData(@NotNull IUpgradeData upgradeData) {
        if (!(upgradeData instanceof SimulationChamberUpgradeData data)
                || data.inputSlotData.size() != INPUT_COUNT + 2
                || data.outputSlotData.size() != OUTPUT_COUNT) {
            super.parseUpgradeData(upgradeData);
            return;
        }
        redstone = data.redstone;
        setControlType(data.controlType);
        energyContainer.setEnergy(data.storedEnergy);
        energySlot.deserializeNBT(data.energySlotData);
        moduleSlot.deserializeNBT(data.inputSlotData.get(0));
        conditionSlot.deserializeNBT(data.inputSlotData.get(1));
        for (int index = 0; index < INPUT_COUNT; index++) {
            inputSlots.get(index).deserializeNBT(data.inputSlotData.get(index + 2));
        }
        for (int index = 0; index < OUTPUT_COUNT; index++) {
            outputSlots.get(index).deserializeNBT(data.outputSlotData.get(index));
        }
        for (ITileComponent component : getComponents()) {
            component.read(data.components);
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
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            level.playSound(null, getBlockPos(), MekanismSounds.HYDRAULIC.get(),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    private record InputReservation(List<BasicInventorySlot> items) {
        private boolean reserve(ExecutionPlan plan) {
            if (!plan.stillValid(items, List.of())) {
                return false;
            }
            plan.consume(items, List.of());
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

        private OutputReservation() {
            items = new ItemStackHandler(OUTPUT_COUNT);
            for (int slot = 0; slot < OUTPUT_COUNT; slot++) {
                items.setStackInSlot(slot, outputSlots.get(slot).getStack().copy());
            }
        }

        private boolean reserve(List<ItemStack> itemResults) {
            ItemStackHandler itemCopy = new ItemStackHandler(OUTPUT_COUNT);
            for (int slot = 0; slot < OUTPUT_COUNT; slot++) {
                itemCopy.setStackInSlot(slot, items.getStackInSlot(slot).copy());
            }
            for (ItemStack result : itemResults) {
                if (!ItemHandlerHelper.insertItemStacked(itemCopy, result.copy(), false).isEmpty()) {
                    return false;
                }
            }
            items = itemCopy;
            return true;
        }

        private List<ItemStack> itemStacks() {
            return java.util.stream.IntStream.range(0, OUTPUT_COUNT)
                    .mapToObj(slot -> items.getStackInSlot(slot).copy())
                    .toList();
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
        private FloatingLong energyPerTick = FloatingLong.create(BASE_ENERGY_USAGE);
        private LaneStatus status = LaneStatus.IDLE;

        private void start(ExecutionPlan plan, int duration, FloatingLong energyPerTick) {
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
            energyPerTick = FloatingLong.create(BASE_ENERGY_USAGE);
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
        Level level = getLevel();
        return common || level != null
                && SimulationRecipeResolver.isSupportedModule(level, stack, false);
    }

}
