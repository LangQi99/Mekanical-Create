package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FanProcessingPolicyTest {
    @Test
    void mirrorsCreateNativeStackTiming() {
        assertEquals(151, FanProcessingPolicy.batchDuration(150, 1));
        assertEquals(151, FanProcessingPolicy.batchDuration(150, 16));
        assertEquals(301, FanProcessingPolicy.batchDuration(150, 17));
        assertEquals(301, FanProcessingPolicy.batchDuration(150, 32));
        assertEquals(451, FanProcessingPolicy.batchDuration(150, 33));
        assertEquals(601, FanProcessingPolicy.batchDuration(150, 64));
    }

    @Test
    void durationNeverFallsBelowOneTick() {
        assertEquals(1, FanProcessingPolicy.batchDuration(0, 1));
        assertEquals(1, FanProcessingPolicy.batchDuration(-20, 64));
        assertEquals(Integer.MAX_VALUE,
                FanProcessingPolicy.batchDuration(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void plannedConflictOperationsRotatePredictablyPerItem() {
        List<Integer> sequence = IntStream.range(0, 8)
                .map(operation -> FanProcessingPolicy.rotationIndex(1, operation, 3))
                .boxed().toList();

        assertEquals(List.of(1, 2, 0, 1, 2, 0, 1, 2), sequence);
        assertThrows(IllegalArgumentException.class,
                () -> FanProcessingPolicy.rotationIndex(0, 0, 0));
    }

    @Test
    void fanBatchesUseTheMachinesFourRealOutputSlots() {
        assertEquals(4, FanProcessingPolicy.OUTPUT_SLOT_COUNT);
    }

    @Test
    void fanInventoryIsSnapshottedOnlyOnceWhenProgressReachesTheEnd() {
        assertFalse(FanProcessingPolicy.shouldSnapshotAtCompletion(true, false, false));
        assertFalse(FanProcessingPolicy.shouldSnapshotAtCompletion(false, true, false));
        assertTrue(FanProcessingPolicy.shouldSnapshotAtCompletion(true, true, false));
        assertFalse(FanProcessingPolicy.shouldSnapshotAtCompletion(true, true, true));
    }

    @Test
    void fanOutputsMustFitBeforeAnyProgressRuns() {
        assertTrue(FanProcessingPolicy.shouldReserveOutputsBeforeProgress(true, false));
        assertTrue(FanProcessingPolicy.shouldReserveOutputsBeforeProgress(false, true));
        assertFalse(FanProcessingPolicy.shouldReserveOutputsBeforeProgress(false, false));

        assertEquals(0, FanProcessingPolicy.progressAfterOutputBlock(true, 149));
        assertEquals(0, FanProcessingPolicy.progressAfterOutputBlock(true, 80));
        assertEquals(80, FanProcessingPolicy.progressAfterOutputBlock(false, 80));
    }
}
