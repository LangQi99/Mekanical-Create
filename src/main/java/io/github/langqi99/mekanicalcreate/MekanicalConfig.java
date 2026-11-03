package io.github.langqi99.mekanicalcreate;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config for the Create stress integration.
 *
 * <p>Machines only run while the group's 通用传动接口 (stress interface) reports
 * that the Create network can cover the group demand. Turning this off restores
 * the old "no stress required" behaviour for players who want it.</p>
 */
public final class MekanicalConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLE_STRESS_REQUIREMENT = BUILDER
            .comment("Require Create rotational stress for Mekanical machines.",
                    "When disabled, machines ignore the stress interface entirely.")
            .define("enableStressRequirement", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MekanicalConfig() {
    }

    public static boolean stressRequirementEnabled() {
        try {
            return ENABLE_STRESS_REQUIREMENT.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }
}
