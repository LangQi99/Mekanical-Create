package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.langqi99.mekanicalcreate.MekanicalConfig;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/** A Create network member at the machine's position. The level stores and saves the Mekanism tile. */
public final class MachineShaft extends KineticBlockEntity {
    private static final String SHAFT_TAG = "MekanicalShaft";
    private static final String GENERATOR_TAG = "GeneratorEnabled";
    // Create Crafts & Additions 1.21.1 defaults: 16,384 SU and 480 FE/t at 256 RPM, with 75% efficiency.
    private static final float GENERATOR_IMPACT = 64;
    private static final double FE_PER_RPM = 360.0 / 256;
    private final TileEntityMekanism machine;
    private boolean generatorEnabled;
    private boolean changingConnections;

    public MachineShaft(TileEntityMekanism machine) {
        super(machine.getType(), machine.getBlockPos(), machine.getBlockState());
        this.machine = machine;
    }

    /** Used only at Create's kinetic lookups. Other callers still see the Mekanism tile. */
    public static BlockEntity resolve(BlockEntity blockEntity) {
        if (blockEntity instanceof KineticMachine machine) {
            MachineShaft shaft = machine.shaft();
            if (!shaft.isRemoved() && !shaft.changingConnections) {
                return shaft;
            }
        }
        return blockEntity;
    }

    public void updateBlockState(BlockState state) {
        BlockState previous = getBlockState();
        if (level != null && !level.isClientSide
                && previous.getBlock() instanceof KineticMachineBlock block
                && block.shaftFace(previous) != block.shaftFace(state)) {
            changingConnections = true;
            try {
                detachKinetics();
                removeSource();
            } finally {
                changingConnections = false;
            }
            updateSpeed = true;
            sendData();
        }
        setBlockState(state);
    }

    @Override
    public void remove() {
        if (hasLevel()) {
            super.remove();
        }
    }

    @Override
    public void setChanged() {
        machine.markForSave();
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = generatorEnabled ? GENERATOR_IMPACT : 0;
        return lastStressApplied;
    }

    void charge(@Nullable IEnergyContainer storage) {
        boolean enabled = storage != null && MekanicalConfig.shaftGenerationEnabled();
        if (generatorEnabled != enabled) {
            generatorEnabled = enabled;
            if (hasNetwork()) {
                KineticNetwork network = getOrCreateNetwork();
                if (network.members.containsKey(this)) {
                    network.updateStressFor(this, calculateStressApplied());
                }
            }
            notifyUpdate();
        }
        if (enabled && storage != null) {
            long generated = generationRate();
            if (generated > 0) {
                storage.insert(generated, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
    }

    private long generationRate() {
        return generatorEnabled ? (long) (Math.abs((int) getSpeed()) * FE_PER_RPM) : 0;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (!generatorEnabled) {
            return added;
        }
        CreateLang.builder().add(Component.translatable("gui.mekanicalcreate.shaft.generation", generationRate()))
                .style(ChatFormatting.AQUA).forGoggles(tooltip);
        return true;
    }

    public void saveToMachine(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        CompoundTag shaftTag = new CompoundTag();
        write(shaftTag, provider, clientPacket);
        tag.put(SHAFT_TAG, shaftTag);
    }

    public void loadFromMachine(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        if (tag.contains(SHAFT_TAG)) {
            read(tag.getCompound(SHAFT_TAG), provider, clientPacket);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putBoolean(GENERATOR_TAG, generatorEnabled);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        generatorEnabled = tag.getBoolean(GENERATOR_TAG);
        super.read(tag, provider, clientPacket);
    }
}
