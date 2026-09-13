package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FactorySpeedScheduleTest {
    @Test
    void speedBeforeOneTickBoundaryDoesNotLoseRoundedWork() {
        // 13 units per tick on a 100-unit recipe is 13 outputs in 100 ticks,
        // not the old ceil(100 / 13) = 8 tick cadence (12 outputs).
        assertEquals(13, outputsAfterTicks(100, 12, 100));
        // Even a fully accelerated long recipe may still take over one tick.
        assertEquals(63, outputsAfterTicks(200, 125, 100));
    }

    @Test
    void crossingOneTickBoundaryPreservesExpectedOutputRate() {
        assertEquals(100, outputsAfterTicks(100, 99, 100));
        assertEquals(101, outputsAfterTicks(100, 100, 100));
        // A calculated 0.5 tick per recipe really means two complete
        // operations, with separate input/output/energy checks, in one tick.
        assertEquals(2, outputsAfterTicks(100, 199, 1));
        assertEquals(10, outputsAfterTicks(100, 199, 5));
        assertEquals(504, outputsAfterTicks(1, 125, 4));
        assertEquals(0, outputsAfterTicks(100, 12, 7));
        assertEquals(1, outputsAfterTicks(100, 12, 8));
    }

    @Test
    void allSupportedSpeedsMatchExactLongRunExpectation() {
        for (int duration : new int[]{1, 2, 3, 7, 20, 100, 200}) {
            for (int cores : new int[]{0, 1, 2, 12, 27, 99, 100, 125}) {
                int ticks = 300;
                assertEquals(ticks * (cores + 1) / duration,
                        outputsAfterTicks(duration, cores, ticks),
                        "duration=" + duration + ", cores=" + cores);
            }
        }
    }

    @Test
    void unusedWorkCanContinueIntoNextRecipeOfDifferentDuration() {
        long remaining = FactorySpeedSchedule.workPerTick(16);
        for (int duration : new int[]{1, 2, 1}) {
            long work = FactorySpeedSchedule.workToAdvance(
                    FactorySpeedSchedule.workPerOperation(duration), 0, remaining);
            remaining -= work;
        }
        assertEquals(52L, remaining);
        assertEquals(8L, FactorySpeedSchedule.workPerOperation(2));
    }

    @Test
    void blockedOperationsDoNotBankUnusedTickWork() {
        long operation = FactorySpeedSchedule.workPerOperation(10);
        long accumulated = FactorySpeedSchedule.workPerTick(2);
        assertEquals(12, accumulated);
        // Output blockage retains only paid partial progress. On unblock,
        // there is exactly one tick's new budget, not a saved burst.
        assertEquals(12, FactorySpeedSchedule.workToAdvance(operation,
                accumulated, FactorySpeedSchedule.workPerTick(2)));
        assertEquals(4, FactorySpeedSchedule.workToAdvance(operation,
                36, FactorySpeedSchedule.workPerTick(2)));
    }

    @Test
    void energyIsChargedForExactlyTheWorkActuallyPerformed() {
        assertEquals(2_000L, FactorySpeedSchedule.operationEnergy(20, 100));
        assertEquals(180L, FactorySpeedSchedule.operationEnergy(20, 9));
        assertEquals(1L, FactorySpeedSchedule.operationEnergy(0, 1));
        assertEquals(Long.MAX_VALUE,
                FactorySpeedSchedule.operationEnergy(Long.MAX_VALUE, 2));
        assertEquals(2_020L, FactorySpeedSchedule.scaledEnergyPerTick(20, 100));
    }

    private static int outputsAfterTicks(int duration, int speedCores, int ticks) {
        long operationWork = FactorySpeedSchedule.workPerOperation(duration);
        long accumulated = 0;
        int outputs = 0;
        for (int tick = 0; tick < ticks; tick++) {
            long budget = FactorySpeedSchedule.workPerTick(speedCores);
            while (budget > 0) {
                long advanced = FactorySpeedSchedule.workToAdvance(
                        operationWork, accumulated, budget);
                accumulated += advanced;
                budget -= advanced;
                if (accumulated != operationWork) {
                    break;
                }
                outputs++;
                accumulated = 0;
            }
        }
        return outputs;
    }
}
