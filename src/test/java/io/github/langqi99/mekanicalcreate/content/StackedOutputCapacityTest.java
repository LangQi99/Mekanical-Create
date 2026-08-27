package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StackedOutputCapacityTest {
    @Test
    void fourOutputSlotsAcceptFourStacksAndRejectTheFifth() {
        StackedOutputCapacity<String> capacity = new StackedOutputCapacity<>(4, List.of());

        for (int stack = 0; stack < 4; stack++) {
            assertTrue(capacity.tryReserve(List.of(item("iron", 64, 64))));
        }
        assertFalse(capacity.tryReserve(List.of(item("iron", 64, 64))));
        assertEquals(256, capacity.snapshot().stream()
                .mapToInt(StackedOutputCapacity.Stack::count).sum());
    }

    @Test
    void existingPartialStackIsFilledBeforeUsingAnotherSlot() {
        StackedOutputCapacity<String> capacity = new StackedOutputCapacity<>(2, List.of(
                item("gold_nugget", 60, 64), StackedOutputCapacity.Stack.empty()));

        assertTrue(capacity.tryReserve(List.of(item("gold_nugget", 68, 64))));
        assertEquals(List.of(64, 64), capacity.snapshot().stream()
                .map(StackedOutputCapacity.Stack::count).toList());
        assertFalse(capacity.tryReserve(List.of(item("gold_nugget", 1, 64))));
    }

    @Test
    void allResultsFromOneInputAreReservedAtomically() {
        StackedOutputCapacity<String> capacity = new StackedOutputCapacity<>(1, List.of());

        assertFalse(capacity.tryReserve(List.of(
                item("iron", 64, 64), item("quartz", 1, 64))));
        assertEquals(0, capacity.snapshot().getFirst().count());
    }

    @Test
    void failedSpeculativeReservationDoesNotMutateOriginal() {
        StackedOutputCapacity<String> original = new StackedOutputCapacity<>(1, List.of());
        StackedOutputCapacity<String> speculative = original.copy();

        assertFalse(speculative.tryReserve(List.of(
                item("iron", 64, 64), item("quartz", 1, 64))));
        assertTrue(original.tryReserve(List.of(item("iron", 64, 64))));
    }

    @Test
    void respectsItemsWithSmallerMaximumStackSizes() {
        StackedOutputCapacity<String> capacity = new StackedOutputCapacity<>(4, List.of());

        assertTrue(capacity.tryReserve(List.of(item("snowball", 48, 16))));
        assertFalse(capacity.tryReserve(List.of(item("snowball", 17, 16))));
        assertEquals(List.of(16, 16, 16, 0), capacity.snapshot().stream()
                .map(StackedOutputCapacity.Stack::count).toList());
    }

    @Test
    void createCrushedGoldWorstCaseUsesOnlyTheCapacitySafePrefix() {
        StackedOutputCapacity<String> capacity = new StackedOutputCapacity<>(4, List.of());

        for (int input = 0; input < 21; input++) {
            assertTrue(capacity.tryReserve(List.of(
                    item("gold_nugget", 9, 64), item("quartz", 1, 64))));
        }
        assertFalse(capacity.tryReserve(List.of(
                item("gold_nugget", 9, 64), item("quartz", 1, 64))));
        assertEquals(189, capacity.snapshot().stream()
                .filter(stack -> "gold_nugget".equals(stack.key()))
                .mapToInt(StackedOutputCapacity.Stack::count).sum());
        assertEquals(21, capacity.snapshot().stream()
                .filter(stack -> "quartz".equals(stack.key()))
                .mapToInt(StackedOutputCapacity.Stack::count).sum());
    }

    @Test
    void aPartialBatchStopsBeforeRollingAnUnsafeProbabilityResult() {
        StackedOutputCapacity<String> capacity = new StackedOutputCapacity<>(2, List.of(
                item("main", 63, 64), item("bonus", 64, 64)));
        AtomicInteger rolls = new AtomicInteger();

        StackedOutputCapacity.Admission<String> admission = capacity.admit(
                List.of(item("main", 1, 64), item("bonus", 1, 64)),
                () -> {
                    rolls.incrementAndGet();
                    return new StackedOutputCapacity.RolledOutput<>(
                            List.of(item("main", 1, 64)), "rolled");
                }, true);

        assertEquals(StackedOutputCapacity.AdmissionStatus.STOPPED,
                admission.status());
        assertEquals(0, rolls.get());
    }

    @Test
    void anExactFirstRollIsLockedWhenAFullOutputCannotAcceptIt() {
        StackedOutputCapacity<String> capacity = new StackedOutputCapacity<>(1,
                List.of(item("other", 64, 64)));
        AtomicInteger rolls = new AtomicInteger();

        StackedOutputCapacity.Admission<String> admission = capacity.admit(
                List.of(item("main", 1, 64), item("bonus", 1, 64)),
                () -> {
                    rolls.incrementAndGet();
                    return new StackedOutputCapacity.RolledOutput<>(
                            List.of(item("main", 1, 64), item("bonus", 1, 64)),
                            "locked-roll");
                }, false);

        assertEquals(StackedOutputCapacity.AdmissionStatus.BLOCKED,
                admission.status());
        assertEquals("locked-roll", admission.value().orElseThrow());
        assertEquals(1, rolls.get());
    }

    @Test
    void optionalOutputsThatDoNotRollLeaveRoomForTheNextSafeUnit() {
        StackedOutputCapacity<String> capacity = new StackedOutputCapacity<>(2, List.of(
                item("main", 62, 64), item("bonus", 63, 64)));
        AtomicInteger rolls = new AtomicInteger();

        StackedOutputCapacity.Admission<String> first = capacity.admit(
                List.of(item("main", 1, 64), item("bonus", 1, 64)),
                () -> {
                    rolls.incrementAndGet();
                    return new StackedOutputCapacity.RolledOutput<>(
                            List.of(item("main", 1, 64)), "first");
                }, false);
        StackedOutputCapacity.Admission<String> second = capacity.admit(
                List.of(item("main", 1, 64), item("bonus", 1, 64)),
                () -> {
                    rolls.incrementAndGet();
                    return new StackedOutputCapacity.RolledOutput<>(List.of(
                            item("main", 1, 64), item("bonus", 1, 64)), "second");
                }, true);
        StackedOutputCapacity.Admission<String> third = capacity.admit(
                List.of(item("main", 1, 64), item("bonus", 1, 64)),
                () -> {
                    rolls.incrementAndGet();
                    return new StackedOutputCapacity.RolledOutput<>(List.of(), "third");
                }, true);

        assertEquals(StackedOutputCapacity.AdmissionStatus.ACCEPTED, first.status());
        assertEquals(StackedOutputCapacity.AdmissionStatus.ACCEPTED, second.status());
        assertEquals(StackedOutputCapacity.AdmissionStatus.STOPPED, third.status());
        assertEquals(2, rolls.get());
        assertEquals(List.of(64, 64), capacity.snapshot().stream()
                .map(StackedOutputCapacity.Stack::count).toList());
    }

    @Test
    void sixteenFullInputStacksConsumeOnlyFourOutputStacksWorth() {
        StackedOutputCapacity<String> capacity = new StackedOutputCapacity<>(4, List.of());
        int[] consumedByInputSlot = new int[16];
        AtomicInteger rolls = new AtomicInteger();
        int accepted = 0;

        for (int inputSlot = 0; inputSlot < consumedByInputSlot.length; inputSlot++) {
            for (int item = 0; item < 64; item++) {
                StackedOutputCapacity.Admission<String> admission = capacity.admit(
                        List.of(item("result", 1, 64)),
                        () -> {
                            rolls.incrementAndGet();
                            return new StackedOutputCapacity.RolledOutput<>(
                                    List.of(item("result", 1, 64)), "result");
                        }, accepted > 0);
                if (admission.status()
                        != StackedOutputCapacity.AdmissionStatus.ACCEPTED) {
                    break;
                }
                consumedByInputSlot[inputSlot]++;
                accepted++;
            }
        }

        assertEquals(256, accepted);
        assertEquals(256, rolls.get());
        assertEquals(List.of(64, 64, 64, 64, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0),
                java.util.Arrays.stream(consumedByInputSlot).boxed().toList());
    }

    private static StackedOutputCapacity.Stack<String> item(
            String key, int count, int maxStackSize) {
        return new StackedOutputCapacity.Stack<>(key, count, maxStackSize);
    }
}
