package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.langqi99.mekanicalcreate.MekanicalConfig;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** A Create network member at the machine's position. The level stores and saves the Mekanism tile. */
public final class MachineShaft extends KineticBlockEntity {
    private static final String SHAFT_TAG = "MekanicalShaft";
    private static final String DEMAND_TAG = "Demand";
    private final TileEntityMekanism machine;
    private long demand;
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
        // Create stores impact per RPM. The machine's recipe demand is expressed in total SU.
        float rpm = Math.abs(getTheoreticalSpeed());
        lastStressApplied = rpm == 0 ? 0 : demand / rpm;
        return lastStressApplied;
    }

    @Override
    public void setSpeed(float speed) {
        float oldSpeed = getTheoreticalSpeed();
        super.setSpeed(speed);
        if (level != null && !level.isClientSide && hasNetwork() && Math.abs(speed) != Math.abs(oldSpeed)) {
            // Source propagation may be in progress. Let Create sync the network after it finishes.
            getOrCreateNetwork().members.replace(this, calculateStressApplied());
            networkDirty = true;
        }
    }

    public void setDemand(long demand) {
        if (this.demand == demand) {
            return;
        }
        this.demand = demand;
        if (hasNetwork()) {
            KineticNetwork network = getOrCreateNetwork();
            if (network.members.containsKey(this)) {
                network.updateStressFor(this, calculateStressApplied());
            }
        }
        notifyUpdate();
    }

    public boolean canRun() {
        return !MekanicalConfig.stressRequirementEnabled() || hasNetwork() && getSpeed() != 0;
    }

    public long supplySu() {
        return hasNetwork() ? (long) capacity : 0L;
    }

    public double workRate() {
        if (!MekanicalConfig.stressRequirementEnabled()) {
            return 1.0;
        }
        double rate = StressDemand.speedFactor(getSpeed());
        return stress > 0 && capacity >= stress * 1.5D ? rate * 2.0 : rate;
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (super.addToTooltip(tooltip, isPlayerSneaking)) {
            return true;
        }
        if (demand <= 0 || getTheoreticalSpeed() != 0) {
            return false;
        }
        CreateLang.builder().add(Component.translatable("gui.mekanicalcreate.shaft.no_rotation", demand))
                .style(ChatFormatting.GOLD).forGoggles(tooltip);
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
        tag.putLong(DEMAND_TAG, demand);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        demand = tag.getLong(DEMAND_TAG);
        super.read(tag, provider, clientPacket);
    }
}
