package io.github.langqi99.mekanicalcreate.content;

import java.util.List;
import java.util.function.Predicate;

/** Pure lane-count policy, kept independent so it can be unit tested. */
final class FactoryLanePolicy {
    private FactoryLanePolicy() {
    }

    static int processCount(int mekanismFactoryProcesses) {
        return Math.max(1, mekanismFactoryProcesses);
    }

    /**
     * Attempts reservations in stable lane order. The reservation callback is
     * atomic: a rejected lane must not change its shared reservation state, so
     * later non-conflicting lanes remain eligible.
     */
    static <T> boolean[] reserveInOrder(List<T> lanes, Predicate<T> tryReserve) {
        boolean[] reserved = new boolean[lanes.size()];
        for (int lane = 0; lane < lanes.size(); lane++) {
            reserved[lane] = tryReserve.test(lanes.get(lane));
        }
        return reserved;
    }
}
