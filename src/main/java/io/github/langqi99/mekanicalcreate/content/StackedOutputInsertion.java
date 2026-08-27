package io.github.langqi99.mekanicalcreate.content;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Real-slot insertion order matching the stacked-first output simulations. */
final class StackedOutputInsertion {
    private StackedOutputInsertion() {
    }

    static <S, R> R insert(
            List<S> slots, R initial,
            Predicate<R> resultEmpty,
            BiPredicate<S, R> compatible,
            Predicate<S> slotEmpty,
            BiFunction<S, R, R> inserter) {
        R remainder = initial;
        for (S slot : slots) {
            if (resultEmpty.test(remainder)) {
                return remainder;
            }
            if (compatible.test(slot, remainder)) {
                remainder = Objects.requireNonNull(inserter.apply(slot, remainder));
            }
        }
        for (S slot : slots) {
            if (resultEmpty.test(remainder)) {
                return remainder;
            }
            if (slotEmpty.test(slot)) {
                remainder = Objects.requireNonNull(inserter.apply(slot, remainder));
            }
        }
        return remainder;
    }
}
