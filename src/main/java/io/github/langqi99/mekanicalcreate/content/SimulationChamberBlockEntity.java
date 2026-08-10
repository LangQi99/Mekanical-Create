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
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismSounds;
import mekanism.common.inventory.container.sync.SyncableFloatingLong;
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
import net.minecraft.world.item.Items;
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
    private boolean planDirty = true;
    @Nullable
    private ExecutionPlan activePlan;

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
        return FloatingLong.create(BASE_ENERGY_USAGE * getTierMultiplier(tier));
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
            invalidatePlan();
        };
        IContentsListener inputListener = () -> {
            listener.onContentsChanged();
            planDirty = true;
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
                (stack, automation) -> isFanModuleInstalled(),
                SimulationChamberBlockEntity::isSupportedCondition,
                configurationListener, 25, 42) {
            @Override
            public InventoryContainerSlot createContainerSlot() {
                return new InventoryContainerSlot(this, 25, 42, ContainerSlotType.NORMAL,
                        null, null, this::setStackUnchecked) {
                    @Override
                    public boolean isActive() {
                        // Keep a populated slot reachable so the player can always
                        // remove its marker after changing the module.
                        return isFanModuleInstalled() || !isEmpty();
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
            setActive(false);
            return;
        }

        if (activePlan != null && planDirty) {
            if (!activePlan.stillValid(inputSlots, List.of())) {
                invalidatePlan();
            } else {
                planDirty = false;
            }
        }

        if (activePlan == null) {
            activePlan = SimulationRecipeResolver.resolve(
                    level, moduleSlot.getStack(), conditionSlot.getStack(), inputSlots,
                    List.of(), false).orElse(null);
            if (activePlan == null) {
                resetIdle();
                return;
            }
            progress = 0;
            duration = MekanismUtils.getTicks(this, getTierDuration(activePlan.duration()));
            energyContainer.setEnergyPerTick(getTierEnergyUsage(activePlan.energyPerTick()));
            planDirty = false;
        }

        if (!canFit(activePlan.itemResults())) {
            setActive(false);
            return;
        }

        FloatingLong energyPerTick = energyContainer.getEnergyPerTick();
        if (energyContainer.extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL).smallerThan(energyPerTick)) {
            setActive(false);
            return;
        }

        energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
        setActive(true);
        progress++;
        if (progress >= duration) {
            if (!activePlan.stillValid(inputSlots, List.of()) || !canFit(activePlan.itemResults())) {
                invalidatePlan();
                setActive(false);
                return;
            }
            activePlan.consume(inputSlots, List.of());
            insertResults(activePlan.itemResults());
            activePlan = null;
            progress = 0;
            duration = DEFAULT_DURATION;
            resetEnergyUsage();
            planDirty = true;
        }
        markForSave();
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
        planDirty = true;
        progress = 0;
        duration = DEFAULT_DURATION;
        resetEnergyUsage();
        if (getLevel() != null) {
            markForSave();
        }
    }

    private int getTierDuration(int baseDuration) {
        BaseTier tier = Attribute.getBaseTier(getBlockType());
        double multiplier = tier == null ? 1.0 : switch (tier) {
            case BASIC -> 0.75;
            case ADVANCED -> 0.5;
            case ELITE -> 1.0 / 3.0;
            case ULTIMATE, CREATIVE -> 0.25;
        };
        return Math.max(1, (int) Math.ceil(Math.max(1, baseDuration) * multiplier));
    }

    private FloatingLong getTierEnergyUsage(long baseEnergyPerTick) {
        return FloatingLong.create(Math.max(1, Math.multiplyExact(baseEnergyPerTick,
                getTierMultiplier(Attribute.getBaseTier(getBlockType())))));
    }

    private void resetEnergyUsage() {
        energyContainer.setEnergyPerTick(getBaseEnergyUsage(
                Attribute.getBaseTier(getBlockType())));
    }

    public boolean isFanModuleInstalled() {
        return moduleSlot.getStack().is(AllBlocks.ENCASED_FAN.asItem());
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

    public double getScaledProgress() {
        return duration <= 0 ? 0 : progress / (double) duration;
    }

    public boolean isEnergyStarved() {
        return progress > 0 && energyContainer.getEnergy().smallerThan(energyContainer.getEnergyPerTick());
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> progress, value -> progress = value));
        container.track(SyncableInt.create(() -> duration, value -> duration = value));
        container.track(SyncableFloatingLong.create(energyContainer::getEnergyPerTick,
                energyContainer::setEnergyPerTick));
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Progress", progress);
        tag.putInt("Duration", duration);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Active plans are deliberately ephemeral. Re-resolve after a reload so a
        // datapack recipe change can never finish an old operation.
        progress = 0;
        duration = DEFAULT_DURATION;
        activePlan = null;
        planDirty = true;
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
        return new MachineUpgradeData(redstone, getControlType(), energyContainer,
                new int[]{progress}, energySlot, storedInputs, storedOutputs, false, getComponents());
    }

    @Override
    public void parseUpgradeData(@NotNull IUpgradeData upgradeData) {
        if (!(upgradeData instanceof MachineUpgradeData data)
                || data.inputSlots.size() != INPUT_COUNT + 2
                || data.outputSlots.size() != OUTPUT_COUNT) {
            super.parseUpgradeData(upgradeData);
            return;
        }
        redstone = data.redstone;
        setControlType(data.controlType);
        energyContainer.setEnergy(data.energyContainer.getEnergy());
        energySlot.deserializeNBT(data.energySlot.serializeNBT());
        moduleSlot.deserializeNBT(data.inputSlots.get(0).serializeNBT());
        conditionSlot.deserializeNBT(data.inputSlots.get(1).serializeNBT());
        for (int index = 0; index < INPUT_COUNT; index++) {
            inputSlots.get(index).deserializeNBT(data.inputSlots.get(index + 2).serializeNBT());
        }
        for (int index = 0; index < OUTPUT_COUNT; index++) {
            outputSlots.get(index).deserializeNBT(data.outputSlots.get(index).serializeNBT());
        }
        for (ITileComponent component : getComponents()) {
            component.read(data.components);
        }
        progress = 0;
        duration = DEFAULT_DURATION;
        activePlan = null;
        planDirty = true;
        resetEnergyUsage();
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            level.playSound(null, getBlockPos(), MekanismSounds.HYDRAULIC.get(),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
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

    private static boolean isSupportedCondition(ItemStack stack) {
        return stack.is(Items.LAVA_BUCKET)
                || stack.is(Items.WATER_BUCKET)
                || stack.is(Items.SOUL_CAMPFIRE)
                || stack.is(Items.CAMPFIRE);
    }
}
