package io.github.langqi99.mekanicalcreate.content;

import mekanism.api.tier.BaseTier;

/**
 * Create stress demand for Mekanical machines.
 *
 * <p>Stress no longer mirrors Mekanism's exponential FE/t curve: with 16 speed
 * upgrades the old formula produced {@code 256 * 10^2 = 25600 SU}, which no
 * realistic Create network can feed. Upgrades now scale stress additively:
 * every speed upgrade adds 25% of the base demand, every energy upgrade
 * subtracts 25%, and the result never drops below 25% of the base.</p>
 *
 * <p>Examples at 256 SU base: 16 speed upgrades -> {@code 256 * (1 + 4) = 1280},
 * 8 speed -> 768. The 25% floor keeps energy upgrades from making a machine
 * almost free.</p>
 *
 * <p>The multiblock factory uses speed cores instead of upgrades; each core also
 * adds 25% of the base demand so both paths read the same way.</p>
 */
public final class StressDemand {
    /** Each speed upgrade adds this fraction of the base demand. */
    public static final double SU_PER_SPEED_UPGRADE = 0.25;
    /** Each energy upgrade removes this fraction of the base demand. */
    public static final double SU_REDUCTION_PER_ENERGY_UPGRADE = 0.25;
    /** Stress never drops below this fraction of the base demand. */
    public static final double MIN_UPGRADE_FACTOR = 0.25;
    /** Each multiblock speed core adds this fraction of the base demand. */
    public static final double SU_PER_SPEED_CORE = 0.25;

    public static final int SIMULATION_CHAMBER_SU = 256;
    public static final int BASIC_FACTORY_SU = 512;
    public static final int ADVANCED_FACTORY_SU = 1024;
    public static final int ELITE_FACTORY_SU = 2048;
    public static final int ULTIMATE_FACTORY_SU = 4096;

    /** A formed fluid factory pays per structure block. */
    public static final int FLUID_FACTORY_SU_PER_BLOCK = 256;

    /** Create treats 64 RPM as the normal working speed. */
    public static final double BASELINE_RPM = 64.0;
    public static final double MIN_SPEED_FACTOR = 0.25;
    public static final double MAX_SPEED_FACTOR = 4.0;

    private StressDemand() {
    }

    public static int baseSuForTier(BaseTier tier) {
        if (tier == null) {
            return SIMULATION_CHAMBER_SU;
        }
        return switch (tier) {
            case BASIC -> BASIC_FACTORY_SU;
            case ADVANCED -> ADVANCED_FACTORY_SU;
            case ELITE -> ELITE_FACTORY_SU;
            case ULTIMATE, CREATIVE -> ULTIMATE_FACTORY_SU;
        };
    }

    /**
     * Linear upgrade scaling: {@code base * (1 + 0.25*speed - 0.25*energy)},
     * floored at 25% of the base so energy upgrades cannot make a machine free.
     */
    public static long scaledSu(long baseSu, int speedUpgrades, int energyUpgrades) {
        if (baseSu <= 0) {
            return 0;
        }
        double factor = 1.0
                + SU_PER_SPEED_UPGRADE * Math.max(0, speedUpgrades)
                - SU_REDUCTION_PER_ENERGY_UPGRADE * Math.max(0, energyUpgrades);
        factor = Math.max(MIN_UPGRADE_FACTOR, factor);
        return (long) Math.ceil(baseSu * factor);
    }

    /**
     * Kept for call sites that still pass Mekanism's multiplier; it is ignored
     * because stress is no longer exponential.
     */
    public static long scaledSu(long baseSu, int speedUpgrades, int energyUpgrades,
                                double ignoredMaxUpgradeMultiplier) {
        return scaledSu(baseSu, speedUpgrades, energyUpgrades);
    }

    /** Fluid factory: every block of the formed structure pays the same rate. */
    public static long fluidFactorySu(int structureVolume) {
        return (long) Math.max(0, structureVolume) * FLUID_FACTORY_SU_PER_BLOCK;
    }

    /** Multiblock speed cores add 25% of the base demand each, same as upgrades. */
    public static long withSpeedCores(long baseSu, int speedCores) {
        if (baseSu <= 0) {
            return 0;
        }
        long scaled = Math.round(baseSu * (1.0 + SU_PER_SPEED_CORE * Math.max(0, speedCores)));
        return Math.max(baseSu / 4, scaled);
    }

    /**
     * 转速 → 效率。锚点由玩法定稿给出：1 RPM = 10%，32 RPM = 100%，
     * 128 RPM = 200%，256 RPM = 400%；超过 256 按 400% 封顶，转速方向不影响。
     */
    public static double speedFactor(float rpm) {
        double r = Math.abs(rpm);
        if (!Double.isFinite(r)) {
            return 0.0;
        }
        if (r <= 1.0) return 0.1;
        if (r >= 256.0) return 4.0;
        if (r <= 32.0) return lerp(0.1, 1.0, (r - 1.0) / 31.0);
        if (r <= 128.0) return lerp(1.0, 2.0, (r - 32.0) / 96.0);
        return lerp(2.0, 4.0, (r - 128.0) / 128.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
