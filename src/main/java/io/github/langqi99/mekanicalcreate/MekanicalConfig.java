package io.github.langqi99.mekanicalcreate;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config for the Create stress integration. */
public final class MekanicalConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLE_STRESS_REQUIREMENT = BUILDER
            .comment("Require Create rotational stress for Mekanical machines.",
                    "When disabled, machines process at their normal rate without rotation.")
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
