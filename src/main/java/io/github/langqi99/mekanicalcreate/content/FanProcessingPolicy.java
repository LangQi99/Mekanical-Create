package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The deliberately small compatibility boundary for simulated fan processing.
 *
 * <p>Both halves of a mode are fixed here: the configuration item selects one
 * of Create's built-in {@link FanProcessingType}s, and that type is only allowed
 * to execute its corresponding built-in recipe type(s). Recipes contributed by
 * datapacks and KubeJS remain compatible because the whitelist targets recipe
 * types rather than recipe namespaces. Third-party fan processing types are not
 * implicitly trusted.</p>
 */
final class FanProcessingPolicy {
    static final int ITEMS_PER_TIME_STEP = 16;
    // Fan batches are sized against these actual item slots. The number of
    // accepted input stacks is deliberately not capped: one recipe can produce
    // multiple stacks, while several inputs can merge into the same output.
    static final int OUTPUT_SLOT_COUNT = 4;

    private FanProcessingPolicy() {
    }

    static Optional<Mode> modeFor(ItemStack condition) {
        if (condition.is(Items.WATER_BUCKET)) {
            return Optional.of(Mode.SPLASHING);
        }
        if (condition.is(Items.SOUL_CAMPFIRE)) {
            return Optional.of(Mode.HAUNTING);
        }
        if (condition.is(Items.CAMPFIRE)) {
            return Optional.of(Mode.SMOKING);
        }
        if (condition.is(Items.LAVA_BUCKET)) {
            return Optional.of(Mode.BLASTING);
        }
        return Optional.empty();
    }

    static Optional<Mode> highestPriorityMode(List<ItemStack> conditions) {
        return conditions.stream()
                .map(FanProcessingPolicy::modeFor)
                .flatMap(Optional::stream)
                .max(Comparator.comparingInt(mode -> mode.fanType().getPriority()));
    }

    static boolean isSupportedCondition(ItemStack condition) {
        return modeFor(condition).isPresent();
    }

    static int batchDuration(int stackCount) {
        int configured = AllConfigs.server().kinetics.fanProcessingTime.get();
        return batchDuration(configured, stackCount);
    }

    /** Mirrors Create's native ceil(stack / 16) fan-processing timing. */
    static int batchDuration(int fanProcessingTime, int stackCount) {
        int steps = Math.max(1, (Math.max(1, stackCount) - 1) / ITEMS_PER_TIME_STEP + 1);
        long createDuration = Math.max(0L, fanProcessingTime) * steps + 1L;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, createDuration));
    }

    /** Pure helper kept visible for deterministic round-robin unit tests. */
    static int rotationIndex(long persistedCursor, int plannedAdvances, int optionCount) {
        if (optionCount <= 0) {
            throw new IllegalArgumentException("optionCount must be positive");
        }
        return (int) Math.floorMod(persistedCursor + plannedAdvances, optionCount);
    }

    static boolean shouldSnapshotAtCompletion(boolean fanProcessing,
                                              boolean reachesCompletion,
                                              boolean alreadySnapshotted) {
        return fanProcessing && reachesCompletion && !alreadySnapshotted;
    }

    static boolean shouldReserveOutputsBeforeProgress(boolean fanProcessing,
                                                      boolean reachesCompletion) {
        return fanProcessing || reachesCompletion;
    }

    static int progressAfterOutputBlock(boolean fanProcessing, int currentProgress) {
        return fanProcessing ? 0 : currentProgress;
    }

    enum Mode {
        SPLASHING(Items.WATER_BUCKET, AllFanProcessingTypes.SPLASHING,
                List.of(AllRecipeTypes.SPLASHING.getType())),
        HAUNTING(Items.SOUL_CAMPFIRE, AllFanProcessingTypes.HAUNTING,
                List.of(AllRecipeTypes.HAUNTING.getType())),
        SMOKING(Items.CAMPFIRE, AllFanProcessingTypes.SMOKING,
                List.of(RecipeType.SMOKING)),
        BLASTING(Items.LAVA_BUCKET, AllFanProcessingTypes.BLASTING,
                List.of(RecipeType.SMELTING, RecipeType.BLASTING));

        private final Item conditionItem;
        private final FanProcessingType fanType;
        private final List<RecipeType<?>> recipeTypes;

        Mode(Item conditionItem, FanProcessingType fanType, List<RecipeType<?>> recipeTypes) {
            this.conditionItem = conditionItem;
            this.fanType = fanType;
            this.recipeTypes = List.copyOf(recipeTypes);
        }

        ItemStack conditionStack() {
            return new ItemStack(conditionItem);
        }

        FanProcessingType fanType() {
            return fanType;
        }

        List<RecipeType<?>> recipeTypes() {
            return recipeTypes;
        }

        boolean allows(RecipeType<?> recipeType) {
            return recipeTypes.stream().anyMatch(allowed -> allowed == recipeType);
        }
    }
}
