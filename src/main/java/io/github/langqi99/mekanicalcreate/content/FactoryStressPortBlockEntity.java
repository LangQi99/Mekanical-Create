package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.langqi99.mekanicalcreate.MekanicalConfig;
import io.github.langqi99.mekanicalcreate.registry.ModStress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * 通用传动接口 - the single kinetic part of a Mekanical machine group.
 *
 * <p>It owns the shaft: Create sees this block as the network member, so stress
 * is real and is released as soon as the block is broken. It walks the connected
 * machine group (face-adjacent machines; other interfaces are connectors) and
 * reports the summed demand of machines that actually have work - idle machines
 * cost nothing.</p>
 */
public class FactoryStressPortBlockEntity extends KineticBlockEntity {
    /** Maximum machines one interface may serve. */
    public static final int MAX_GROUP_SIZE = 64;
    private static final int RECOMPUTE_INTERVAL = 5;

    private long demand;
    /** Last values pushed to clients for the interface screen. */
    private long syncedCapacity;
    private int recomputeTimer;

    public FactoryStressPortBlockEntity(BlockPos pos, BlockState state) {
        this(ModStress.stressPortType(), pos, state);
    }

    public FactoryStressPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isSupplied() {
        return getSpeed() != 0;
    }

    /** Summed stress demand of the served machine group, in SU. */
    public long groupDemand() {
        return demand;
    }

    /** Stress capacity of the network this interface sits in, in SU. */
    public long networkCapacity() {
        return hasNetwork() ? (long) getOrCreateNetwork().calculateCapacity() : 0L;
    }

    /** Diagnostic: how many machines the BFS actually found. */
    public int debugGroupMembers() {
        if (level == null) {
            return 0;
        }
        int found = 0;
        for (Direction side : Direction.values()) {
            net.minecraft.world.level.block.entity.BlockEntity neighbour =
                    level.getBlockEntity(worldPosition.relative(side));
            if (neighbour instanceof SimulationChamberBlockEntity
                    || neighbour instanceof MekanicalFactoryControllerBlockEntity) {
                found++;
            }
        }
        return found;
    }

    /** Group demand including idle machines, used for the GUI readout only. */
    public long totalGroupDemand() {
        return level == null || level.isClientSide
                ? (long) clientDemand()
                : computeGroupDemand(level, false);
    }

    public long clientDemand() {
        return demand;
    }

    public long clientCapacity() {
        return syncedCapacity;
    }

    /** True when the network is starved: demand exists but cannot be covered. */
    public boolean isStarved() {
        return demand > 0L && networkCapacity() < demand;
    }

    /** True when capacity is at least 1.5x the demand: overclocked, 2x speed. */
    public boolean isOverclocked() {
        return demand > 0L && networkCapacity() >= Math.round(demand * 1.5D);
    }

    @Override
    public float calculateStressApplied() {
        return demand <= 0L ? 0.0F : (float) demand;
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag,
                         net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putLong("StressDemand", demand);
        tag.putLong("StressCapacity", syncedCapacity);
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag,
                        net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        demand = tag.getLong("StressDemand");
        syncedCapacity = tag.getLong("StressCapacity");
    }

    public void serverTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        if (--recomputeTimer > 0) {
            return;
        }
        recomputeTimer = RECOMPUTE_INTERVAL;
        long recomputed = MekanicalConfig.stressRequirementEnabled() ? computeGroupDemand(level, true) : 0L;
        if (recomputed == demand) {
            return;
        }
        demand = recomputed;
        // 不再手工 updateStressFor：Create 每 tick 会读 calculateStressApplied()，
        // 手工写入会和它自己的重算叠加，导致"UI 说有富余、护目镜说过载"。
        long capacity = networkCapacity();
        if (capacity != syncedCapacity) {
            syncedCapacity = capacity;
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
        networkDirty = true;
    }

    /**
     * Breadth-first walk over the machine group. Machines are nodes; interfaces
     * are pass-through connectors so one interface may sit between two machines.
     */
    private long computeGroupDemand(Level level, boolean onlyWorking) {
        long total = 0L;
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition);
        visited.add(worldPosition);
        int machines = 0;
        while (!queue.isEmpty() && machines < MAX_GROUP_SIZE) {
            BlockPos current = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = current.relative(side);
                if (!visited.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockEntity found = level.getBlockEntity(next);
                if (found instanceof SimulationChamberBlockEntity machine) {
                    total += onlyWorking ? machine.activeStressSu() : machine.requiredStressSu();
                    machines++;
                    queue.add(next);
                } else if (found instanceof MekanicalFactoryControllerBlockEntity controller) {
                    total += controller.requiredStressSu();
                    machines++;
                    queue.add(next);
                } else if (found instanceof FactoryStressPortBlockEntity) {
                    // Another interface joins the group but never adds demand.
                    queue.add(next);
                }
            }
        }
        return total;
    }
}
