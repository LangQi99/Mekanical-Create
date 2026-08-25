package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationFactoryLaneTest {
    @Test
    void usesMekanismFactoryProcessCounts() {
        assertEquals(1, FactoryLanePolicy.processCount(0));
        assertEquals(3, FactoryLanePolicy.processCount(3));
        assertEquals(5, FactoryLanePolicy.processCount(5));
        assertEquals(7, FactoryLanePolicy.processCount(7));
        assertEquals(9, FactoryLanePolicy.processCount(9));
    }

    @Test
    void failedSharedReservationOnlyBlocksThatLane() {
        int[] remaining = {3};
        boolean[] accepted = FactoryLanePolicy.reserveInOrder(List.of(4, 2, 1), demand -> {
            if (demand > remaining[0]) {
                return false;
            }
            remaining[0] -= demand;
            return true;
        });

        assertArrayEquals(new boolean[]{false, true, true}, accepted);
        assertEquals(0, remaining[0]);
    }
}
