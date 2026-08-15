package io.github.langqi99.mekanicalcreate.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Selects only among candidates tied at the highest non-ID preference rank. */
final class HighestPriorityRoundRobin {
    private static final String GROUP_SEPARATOR = "\u001f";

    private HighestPriorityRoundRobin() {
    }

    static <T> Selection<T> select(List<T> values, Comparator<T> preference,
                                   Function<T, String> stableId,
                                   RecipeRoundRobinState state) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot select from an empty candidate list");
        }
        T best = null;
        List<T> tied = new ArrayList<>();
        for (T value : values) {
            if (best == null) {
                best = value;
                tied.add(value);
                continue;
            }
            int comparison = preference.compare(value, best);
            if (comparison > 0) {
                best = value;
                tied.clear();
                tied.add(value);
            } else if (comparison == 0) {
                tied.add(value);
            }
        }
        tied.sort(Comparator.comparing(stableId));
        if (tied.size() == 1) {
            return new Selection<>(tied.getFirst(), null, 1);
        }
        String group = tied.stream().map(stableId).reduce((left, right) ->
                left + GROUP_SEPARATOR + right).orElseThrow();
        int index = (int) Math.floorMod(state.cursor(group), tied.size());
        return new Selection<>(tied.get(index), group, tied.size());
    }

    record Selection<T>(T value, String group, int optionCount) {
        boolean rotates() {
            return group != null;
        }
    }
}
