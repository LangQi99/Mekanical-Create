package io.github.langqi99.mekanicalcreate.content;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;

/** Greedy allocator for requirement sets proven not to overlap. */
final class DisjointRequirementAllocator {
    private DisjointRequirementAllocator() {
    }

    @Nullable
    static <S> Allocation allocate(List<S> slots,
                                   ToIntFunction<S> capacity,
                                   int requirementCount,
                                   IntUnaryOperator demand,
                                   BiPredicate<S, Integer> matches) {
        int[] usedBySlot = new int[slots.size()];
        int[] firstSlotByRequirement = new int[requirementCount];
        Arrays.fill(firstSlotByRequirement, -1);
        for (int requirement = 0; requirement < requirementCount; requirement++) {
            int remaining = demand.applyAsInt(requirement);
            if (remaining < 0) {
                throw new IllegalArgumentException("Requirement demand cannot be negative");
            }
            for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
                S value = slots.get(slot);
                if (!matches.test(value, requirement)) {
                    continue;
                }
                int available = Math.max(0, capacity.applyAsInt(value) - usedBySlot[slot]);
                int used = Math.min(available, remaining);
                if (used > 0) {
                    usedBySlot[slot] += used;
                    remaining -= used;
                    if (firstSlotByRequirement[requirement] < 0) {
                        firstSlotByRequirement[requirement] = slot;
                    }
                }
            }
            if (remaining > 0) {
                return null;
            }
        }
        return new Allocation(usedBySlot, firstSlotByRequirement);
    }

    record Allocation(int[] usedBySlot, int[] firstSlotByRequirement) {
        Allocation {
            usedBySlot = usedBySlot.clone();
            firstSlotByRequirement = firstSlotByRequirement.clone();
        }

        @Override
        public int[] usedBySlot() {
            return usedBySlot.clone();
        }

        @Override
        public int[] firstSlotByRequirement() {
            return firstSlotByRequirement.clone();
        }
    }
}
