package io.github.langqi99.mekanicalcreate.content;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/**
 * Atomic item-output simulation used while a fan batch is still being built.
 *
 * <p>Each fan item may produce more than one result. A whole item's results
 * are therefore tried against a copy first and are committed to the simulated
 * output only when every result fits.</p>
 */
final class FanOutputCapacity {
    private static final int DEFAULT_SLOT_LIMIT = 64;
    private final StackedOutputCapacity<ItemKey> contents;

    FanOutputCapacity(int slotCount, List<ItemStack> initialContents) {
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive");
        }
        if (initialContents.size() > slotCount) {
            throw new IllegalArgumentException("initial contents exceed slot count");
        }
        List<StackedOutputCapacity.Stack<ItemKey>> initial = initialContents.stream()
                .map(FanOutputCapacity::describe)
                .toList();
        contents = new StackedOutputCapacity<>(slotCount, initial);
    }

    StackedOutputCapacity.Admission<List<ItemStack>> admit(
            List<ItemStack> possibleResults,
            Supplier<List<ItemStack>> roller,
            boolean batchAlreadyAccepted) {
        return contents.admit(possibleResults.stream()
                        .map(FanOutputCapacity::describe)
                        .toList(),
                () -> {
                    List<ItemStack> rolled = roller.get();
                    return new StackedOutputCapacity.RolledOutput<>(rolled.stream()
                            .map(FanOutputCapacity::describe)
                            .toList(), rolled);
                }, batchAlreadyAccepted);
    }

    private static StackedOutputCapacity.Stack<ItemKey> describe(ItemStack stack) {
        if (stack.isEmpty()) {
            return StackedOutputCapacity.Stack.empty();
        }
        return new StackedOutputCapacity.Stack<>(new ItemKey(stack), stack.getCount(),
                Math.min(DEFAULT_SLOT_LIMIT, stack.getMaxStackSize()));
    }

    private static final class ItemKey {
        private final ItemStack exemplar;
        private final int hash;

        private ItemKey(ItemStack exemplar) {
            this.exemplar = exemplar.copyWithCount(1);
            hash = ItemStack.hashItemAndComponents(this.exemplar);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof ItemKey key
                    && ItemStack.isSameItemSameComponents(exemplar, key.exemplar);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
