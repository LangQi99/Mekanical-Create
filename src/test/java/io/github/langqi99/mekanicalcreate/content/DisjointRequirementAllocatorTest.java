package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DisjointRequirementAllocatorTest {
    private record Slot(String item, int count) {
    }

    @Test
    void splitsOneRequirementAcrossMultipleSlots() {
        List<Slot> slots = List.of(new Slot("iron", 2), new Slot("iron", 3),
                new Slot("gold", 4));

        DisjointRequirementAllocator.Allocation allocation =
                DisjointRequirementAllocator.allocate(slots, Slot::count, 2,
                        requirement -> requirement == 0 ? 4 : 3,
                        (slot, requirement) -> slot.item().equals(
                                requirement == 0 ? "iron" : "gold"));

        assertArrayEquals(new int[]{2, 2, 3}, allocation.usedBySlot());
        assertArrayEquals(new int[]{0, 2}, allocation.firstSlotByRequirement());
    }

    @Test
    void rejectsInsufficientCapacity() {
        List<Slot> slots = List.of(new Slot("iron", 2));

        assertNull(DisjointRequirementAllocator.allocate(slots, Slot::count, 1,
                requirement -> 3, (slot, requirement) -> true));
    }

    @Test
    void handlesRequirementsSharingOneHomogeneousPool() {
        List<Slot> slots = List.of(new Slot("plank", 8));

        DisjointRequirementAllocator.Allocation allocation =
                DisjointRequirementAllocator.allocate(slots, Slot::count, 2,
                        requirement -> 4, (slot, requirement) -> true);

        assertArrayEquals(new int[]{8}, allocation.usedBySlot());
        assertArrayEquals(new int[]{0, 0}, allocation.firstSlotByRequirement());
    }

    @Test
    void rejectsNegativeDemand() {
        assertThrows(IllegalArgumentException.class, () ->
                DisjointRequirementAllocator.allocate(List.of(new Slot("iron", 1)),
                        Slot::count, 1, requirement -> -1,
                        (slot, requirement) -> true));
    }
}
