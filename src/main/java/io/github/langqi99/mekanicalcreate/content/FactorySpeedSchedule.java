package io.github.langqi99.mekanicalcreate.content;

/**
 * Exact fixed-point speed calculations for the multiblock factory.
 *
 * <p>One speed core contributes one additional normal processing tick of work.
 * Once a recipe already completes in a single game tick, any remaining work
 * is converted into additional operations instead of being discarded.</p>
 */
final class FactorySpeedSchedule {
    private static final long SPEED_DENOMINATOR = 4L;

    private FactorySpeedSchedule() {
    }

    static int adjustedDuration(int recipeDuration, int speedCoreCount) {
        return (int) Math.max(1L, ceilDiv(workPerOperation(recipeDuration),
                workPerTick(speedCoreCount)));
    }

    static boolean overflowsIntoParallelism(int recipeDuration, int speedCoreCount) {
        return workPerTick(speedCoreCount) > workPerOperation(recipeDuration);
    }

    /**
     * Allocates this tick's whole operations while retaining only the exact
     * fractional work remainder. Integer work that cannot run because a
     * machine is blocked is intentionally not banked for a later burst.
     */
    static OverflowBudget overflowBudget(int recipeDuration, int speedCoreCount,
                                         long workRemainder) {
        long workPerOperation = workPerOperation(recipeDuration);
        long availableWork = availableWork(speedCoreCount, workRemainder);
        long operations = availableWork / workPerOperation;
        long remainder = availableWork % workPerOperation;
        return new OverflowBudget((int) Math.min(Integer.MAX_VALUE, operations), remainder);
    }

    static long availableWork(int speedCoreCount, long workRemainder) {
        return saturatedAdd(Math.max(0, workRemainder), workPerTick(speedCoreCount));
    }

    static long workPerTick(int speedCoreCount) {
        return SPEED_DENOMINATOR * (1L + Math.max(0, speedCoreCount));
    }

    static long workPerOperation(int recipeDuration) {
        return saturatedMultiply(Math.max(1, recipeDuration), SPEED_DENOMINATOR);
    }

    static long operationEnergy(long energyPerTick, int recipeDuration) {
        return saturatedMultiply(Math.max(1, energyPerTick), Math.max(1, recipeDuration));
    }

    static long scaledEnergyPerTick(long energyPerTick, int speedCoreCount) {
        return saturatedMultiply(Math.max(1, energyPerTick),
                1L + Math.max(0, speedCoreCount));
    }

    private static long ceilDiv(long numerator, long denominator) {
        return 1L + (numerator - 1L) / denominator;
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    record OverflowBudget(int operations, long workRemainder) {
    }
}
