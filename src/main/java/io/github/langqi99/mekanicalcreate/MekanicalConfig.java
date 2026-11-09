package io.github.langqi99.mekanicalcreate;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config for the shaft generation. */
public final class MekanicalConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLE_SHAFT_GENERATION = BUILDER
            .comment("Generate Forge Energy inside Mekanical machines from Create rotation.",
                    "When disabled, shafts transmit rotation without adding generator load.")
            .define("enableStressRequirement", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MekanicalConfig() {
    }

    public static boolean shaftGenerationEnabled() {
        try {
            return ENABLE_SHAFT_GENERATION.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }
}
