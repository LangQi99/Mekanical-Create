package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FactorySpeedScheduleTest {
    @Test
    void shortensRecipesUntilOneTick() {
        assertEquals(100, FactorySpeedSchedule.adjustedDuration(100, 0));
        assertEquals(20, FactorySpeedSchedule.adjustedDuration(100, 4));
        assertEquals(1, FactorySpeedSchedule.adjustedDuration(1, 0));
        assertFalse(FactorySpeedSchedule.overflowsIntoParallelism(1, 0));
    }

    @Test
    void convertsExcessSpeedIntoExactFractionalParallelism() {
        long remainder = 0;
        FactorySpeedSchedule.OverflowBudget first =
                FactorySpeedSchedule.overflowBudget(2, 2, remainder);
        FactorySpeedSchedule.OverflowBudget second =
                FactorySpeedSchedule.overflowBudget(2, 2, first.workRemainder());

        assertTrue(FactorySpeedSchedule.overflowsIntoParallelism(2, 2));
        assertEquals(1, first.operations());
        assertEquals(4, first.workRemainder());
        assertEquals(2, second.operations());
        assertEquals(0, second.workRemainder());
    }

    @Test
    void allPhysicallyPossibleSpeedCoresRemainEffective() {
        assertEquals(8, FactorySpeedSchedule.adjustedDuration(100, 12));

        long remainder = 0;
        int operations = 0;
        for (int tick = 0; tick < 4; tick++) {
            FactorySpeedSchedule.OverflowBudget budget =
                    FactorySpeedSchedule.overflowBudget(1, 125, remainder);
            operations += budget.operations();
            remainder = budget.workRemainder();
        }
        assertEquals(504, operations);
        assertEquals(0, remainder);
    }

    @Test
    void oneHundredCoresMakeAHundredTickRecipeSlightlyFasterThanOnePerTick() {
        assertEquals(1, FactorySpeedSchedule.adjustedDuration(100, 100));
        assertTrue(FactorySpeedSchedule.overflowsIntoParallelism(100, 100));

        long remainder = 0;
        int operations = 0;
        for (int tick = 0; tick < 100; tick++) {
            FactorySpeedSchedule.OverflowBudget budget =
                    FactorySpeedSchedule.overflowBudget(100, 100, remainder);
            operations += budget.operations();
            remainder = budget.workRemainder();
        }
        assertEquals(101, operations);
        assertEquals(0, remainder);
    }

    @Test
    void everyParallelOperationPaysAWholeRecipesEnergy() {
        assertEquals(2_000L, FactorySpeedSchedule.operationEnergy(20, 100));
        assertEquals(1L, FactorySpeedSchedule.operationEnergy(0, 1));
        assertEquals(Long.MAX_VALUE,
                FactorySpeedSchedule.operationEnergy(Long.MAX_VALUE, 2));
        assertEquals(2_020L, FactorySpeedSchedule.scaledEnergyPerTick(20, 100));
        assertEquals(Long.MAX_VALUE,
                FactorySpeedSchedule.scaledEnergyPerTick(Long.MAX_VALUE, 100));
    }

    @Test
    void oneTickBudgetCanPayRoundRobinRecipesWithDifferentDurations() {
        long work = FactorySpeedSchedule.availableWork(16, 0);
        work -= FactorySpeedSchedule.workPerOperation(1);
        work -= FactorySpeedSchedule.workPerOperation(2);
        work -= FactorySpeedSchedule.workPerOperation(1);

        assertEquals(52L, work);
        assertEquals(120L, FactorySpeedSchedule.availableWork(16, work));
        assertEquals(8L, FactorySpeedSchedule.workPerOperation(2));
    }

    @Test
    void carriesFractionalWorkIntoALongerRoundRobinRecipe() {
        long remainder = FactorySpeedSchedule.availableWork(1, 0)
                - FactorySpeedSchedule.workPerOperation(1);
        assertEquals(4L, remainder);

        long nextTickWork = FactorySpeedSchedule.availableWork(1, remainder);
        assertEquals(12L, nextTickWork);
        assertEquals(12L, FactorySpeedSchedule.workPerOperation(3));
        assertEquals(0L, nextTickWork - FactorySpeedSchedule.workPerOperation(3));
    }
}
