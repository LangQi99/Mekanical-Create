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
    void mirrorsCreateStackTimingBeforeApplyingTenTimesProcessingTime() {
        assertEquals(1_510, FanProcessingPolicy.batchDuration(150, 1));
        assertEquals(1_510, FanProcessingPolicy.batchDuration(150, 16));
        assertEquals(3_010, FanProcessingPolicy.batchDuration(150, 17));
        assertEquals(3_010, FanProcessingPolicy.batchDuration(150, 32));
        assertEquals(4_510, FanProcessingPolicy.batchDuration(150, 33));
        assertEquals(6_010, FanProcessingPolicy.batchDuration(150, 64));
    }

    @Test
    void durationNeverFallsBelowOneTick() {
        assertEquals(10, FanProcessingPolicy.batchDuration(0, 1));
        assertEquals(10, FanProcessingPolicy.batchDuration(-20, 64));
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
    void oneFanOperationClaimsAtMostFourOutputGroups() {
        assertTrue(FanProcessingPolicy.canAcceptOutputGroup(0));
        assertTrue(FanProcessingPolicy.canAcceptOutputGroup(3));
        assertFalse(FanProcessingPolicy.canAcceptOutputGroup(4));
        assertFalse(FanProcessingPolicy.canAcceptOutputGroup(5));
        assertFalse(FanProcessingPolicy.canAcceptOutputGroup(-1));
        assertEquals(4, FanProcessingPolicy.MAX_OUTPUT_GROUPS);
    }

    @Test
    void fanInventoryIsSnapshottedOnlyOnceWhenProgressReachesTheEnd() {
        assertFalse(FanProcessingPolicy.shouldSnapshotAtCompletion(true, false, false));
        assertFalse(FanProcessingPolicy.shouldSnapshotAtCompletion(false, true, false));
        assertTrue(FanProcessingPolicy.shouldSnapshotAtCompletion(true, true, false));
        assertFalse(FanProcessingPolicy.shouldSnapshotAtCompletion(true, true, true));
    }
}
