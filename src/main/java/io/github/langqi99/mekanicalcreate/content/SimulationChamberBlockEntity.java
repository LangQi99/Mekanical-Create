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
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismSounds;
import mekanism.common.inventory.container.sync.SyncableLong;
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
    private boolean planDirty = true;
    private long observedRecipeEpoch = SimulationRecipeResolver.cacheEpoch();
    private final RecipeLookupThrottle lookupThrottle = new RecipeLookupThrottle();
    private final RecipeRoundRobinState roundRobinState = new RecipeRoundRobinState();
    private boolean repeatEligible;
    @Nullable
    private ExecutionPlan activePlan;
    @Nullable
    private ExecutionPlan repeatPlan;

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
        return BASE_ENERGY_USAGE * getTierMultiplier(tier);
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
            setActive(false);
            return sendUpdatePacket;
        }

        long recipeEpoch = SimulationRecipeResolver.cacheEpoch();
        if (observedRecipeEpoch != recipeEpoch) {
            observedRecipeEpoch = recipeEpoch;
            lookupThrottle.clear();
            lookupThrottle.deferUntil(level.getGameTime()
                    + Math.floorMod(getBlockPos().hashCode(), 5));
            invalidatePlan();
        }

        if (activePlan != null && planDirty) {
            if (!activePlan.stillValid(inputSlots,
                    supportsFluids() ? inputFluidTanks : List.of())) {
                invalidatePlan();
            } else {
                planDirty = false;
            }
        }

        if (activePlan == null) {
            if (!planDirty) {
                resetIdle();
                return sendUpdatePacket;
            }
            if (lookupThrottle.shouldWait(level.getGameTime())) {
                if (lookupThrottle.isExplicitWait(level.getGameTime())) {
                    SimulationRecipeResolver.recordReloadDeferral();
                } else {
                    SimulationRecipeResolver.recordDebounceDeferral();
                }
                resetIdle();
                return sendUpdatePacket;
            }
            List<? extends IExtendedFluidTank> activeInputFluids = supportsFluids()
                    ? inputFluidTanks : List.of();
            if (repeatEligible && repeatPlan != null) {
                activePlan = repeatPlan.repeat(level, inputSlots, activeInputFluids)
                        .orElse(null);
            }
            repeatEligible = false;
            repeatPlan = null;
            if (activePlan == null) {
                activePlan = SimulationRecipeResolver.resolve(
                        level, moduleSlot.getStack(), conditionSlot.getStack(), inputSlots,
                        activeInputFluids, supportsFluids(), roundRobinState).orElse(null);
            }
            planDirty = false;
            lookupThrottle.resolved();
            if (activePlan == null) {
                resetIdle();
                return sendUpdatePacket;
            }
            progress = 0;
            duration = MekanismUtils.getTicks(this, getTierDuration(activePlan.duration()));
            energyContainer.setEnergyPerTick(getTierEnergyUsage(activePlan.energyPerTick()));
        }

        if (!canFit(activePlan.itemResults(), activePlan.fluidResults())) {
            setActive(false);
            return sendUpdatePacket;
        }

        long energyPerTick = energyContainer.getEnergyPerTick();
        if (energyContainer.extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL) != energyPerTick) {
            setActive(false);
            return sendUpdatePacket;
        }

        energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
        setActive(true);
        progress++;
        if (progress >= duration) {
            if (!activePlan.stillValid(inputSlots,
                    supportsFluids() ? inputFluidTanks : List.of())
                    || !canFit(activePlan.itemResults(), activePlan.fluidResults())) {
                invalidatePlan();
                setActive(false);
                return sendUpdatePacket;
            }
            ExecutionPlan completedPlan = activePlan;
            activePlan.consume(inputSlots, supportsFluids() ? inputFluidTanks : List.of());
            insertResults(activePlan.itemResults());
            insertFluidResults(activePlan.fluidResults());
            activePlan.advanceRoundRobin(roundRobinState);
            activePlan = null;
            progress = 0;
            duration = DEFAULT_DURATION;
            resetEnergyUsage();
            planDirty = true;
            repeatPlan = completedPlan;
            repeatEligible = true;
            lookupThrottle.clear();
        }
        markForSave();
        return sendUpdatePacket;
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

    private void resetIdle() {
        if (progress != 0 || duration != DEFAULT_DURATION || getActive()) {
            progress = 0;
            duration = DEFAULT_DURATION;
            resetEnergyUsage();
            setActive(false);
            markForSave();
        } else {
            setActive(false);
        }
    }

    private void invalidatePlan() {
        activePlan = null;
        repeatPlan = null;
        repeatEligible = false;
        planDirty = true;
        progress = 0;
        duration = DEFAULT_DURATION;
        resetEnergyUsage();
        if (getLevel() != null) {
            markForSave();
        }
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

    private int getTierDuration(int baseDuration) {
        BaseTier tier = Attribute.getBaseTier(getBlockHolder());
        double multiplier = tier == null ? 1.0 : switch (tier) {
            case BASIC -> 0.75;
            case ADVANCED -> 0.5;
            case ELITE -> 1.0 / 3.0;
            case ULTIMATE, CREATIVE -> 0.25;
        };
        return Math.max(1, (int) Math.ceil(Math.max(1, baseDuration) * multiplier));
    }

    private long getTierEnergyUsage(long baseEnergyPerTick) {
        return Math.max(1, Math.multiplyExact(baseEnergyPerTick,
                getTierMultiplier(Attribute.getBaseTier(getBlockHolder()))));
    }

    private void resetEnergyUsage() {
        energyContainer.setEnergyPerTick(getBaseEnergyUsage(
                Attribute.getBaseTier(getBlockHolder())));
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

    public double getScaledProgress() {
        return duration <= 0 ? 0 : progress / (double) duration;
    }

    public boolean isEnergyStarved() {
        return progress > 0 && energyContainer.getEnergy() < energyContainer.getEnergyPerTick();
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> progress, value -> progress = value));
        container.track(SyncableInt.create(() -> duration, value -> duration = value));
        container.track(SyncableLong.create(energyContainer::getEnergyPerTick,
                energyContainer::setEnergyPerTick));
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
        activePlan = null;
        repeatPlan = null;
        repeatEligible = false;
        planDirty = true;
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
        activePlan = null;
        repeatPlan = null;
        repeatEligible = false;
        planDirty = true;
        lookupThrottle.clear();
        resetEnergyUsage();
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
