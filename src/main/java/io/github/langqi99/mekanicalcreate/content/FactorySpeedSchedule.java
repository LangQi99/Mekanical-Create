package io.github.langqi99.mekanicalcreate.content;

/**
 * Exact fixed-point speed calculations for the multiblock factory.
 *
 * <p>One speed core contributes one additional normal processing tick of work.
 * Unspent work crosses operation boundaries within the same game tick, so
 * neither a rounded duration nor the one-tick floor discards throughput.</p>
 */
final class FactorySpeedSchedule {
    private static final long SPEED_DENOMINATOR = 4L;

    private FactorySpeedSchedule() {
    }

    static long workPerTick(int speedCoreCount) {
        return SPEED_DENOMINATOR * (1L + Math.max(0, speedCoreCount));
    }

    static long workPerOperation(int recipeDuration) {
        return saturatedMultiply(Math.max(1, recipeDuration), SPEED_DENOMINATOR);
    }

    static long workToAdvance(long operationWork, long alreadyCompletedWork,
                              long availableWork) {
        return Math.min(Math.max(0, availableWork),
                Math.max(0, operationWork - alreadyCompletedWork));
    }

    static long operationEnergy(long energyPerTick, long recipeDuration) {
        return saturatedMultiply(Math.max(1, energyPerTick), Math.max(1, recipeDuration));
    }

    static long scaledEnergyPerTick(long energyPerTick, int speedCoreCount) {
        return saturatedMultiply(Math.max(1, energyPerTick),
                1L + Math.max(0, speedCoreCount));
    }

    private static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
