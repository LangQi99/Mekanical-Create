package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeRoundRobinStateTest {
    @Test
    void snapshotCanBeRestored() {
        RecipeRoundRobinState original = new RecipeRoundRobinState();
        original.advance("iron");
        original.advance("iron");
        original.advance("copper");

        RecipeRoundRobinState restored = new RecipeRoundRobinState();
        restored.restore(original.snapshot());

        assertEquals(2L, restored.cursor("iron"));
        assertEquals(1L, restored.cursor("copper"));
    }

    @Test
    void restoredStateIsBounded() {
        Map<String, Long> oversized = new LinkedHashMap<>();
        for (int index = 0; index < RecipeRoundRobinState.MAX_GROUPS + 20; index++) {
            oversized.put("group-" + index, (long) index);
        }
        RecipeRoundRobinState state = new RecipeRoundRobinState();
        state.restore(oversized);

        assertEquals(RecipeRoundRobinState.MAX_GROUPS, state.snapshot().size());
    }
}
