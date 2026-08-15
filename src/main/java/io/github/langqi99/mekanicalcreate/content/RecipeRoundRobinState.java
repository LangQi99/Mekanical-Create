package io.github.langqi99.mekanicalcreate.content;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-machine cursors for groups of recipes that are otherwise equally
 * preferred. Keeping one cursor per group prevents an unrelated ambiguous
 * recipe from changing which result another input produces next.
 */
final class RecipeRoundRobinState {
    static final int MAX_GROUPS = 128;

    private final LinkedHashMap<String, Long> cursors = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_GROUPS;
        }
    };

    long cursor(String group) {
        return cursors.getOrDefault(group, 0L);
    }

    void advance(String group) {
        cursors.compute(group, (ignored, cursor) -> cursor == null ? 1L : cursor + 1L);
    }

    Map<String, Long> snapshot() {
        return Map.copyOf(cursors);
    }

    void restore(Map<String, Long> restored) {
        cursors.clear();
        restored.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(MAX_GROUPS)
                .forEach(entry -> cursors.put(entry.getKey(), entry.getValue()));
    }
}
