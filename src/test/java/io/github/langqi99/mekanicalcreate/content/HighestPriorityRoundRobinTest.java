package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighestPriorityRoundRobinTest {
    private static final Comparator<Option> COMPLEXITY =
            Comparator.comparingInt(Option::complexity);

    @Test
    void lowerComplexityRecipesNeverJoinRotation() {
        RecipeRoundRobinState state = new RecipeRoundRobinState();
        List<Option> recipes = List.of(
                new Option("sugar", 1),
                new Option("sugar_cane_block", 9));

        for (int operation = 0; operation < 4; operation++) {
            HighestPriorityRoundRobin.Selection<Option> selection = select(recipes, state);
            assertEquals("sugar_cane_block", selection.value().id());
            assertFalse(selection.rotates());
        }
    }

    @Test
    void equalHighestComplexityRecipesRotateInStableIdOrder() {
        RecipeRoundRobinState state = new RecipeRoundRobinState();
        List<Option> recipes = List.of(
                new Option("mod_c:block", 9),
                new Option("mod_a:block", 9),
                new Option("mod_b:block", 9),
                new Option("minecraft:sugar", 1));

        HighestPriorityRoundRobin.Selection<Option> first = select(recipes, state);
        assertEquals("mod_a:block", first.value().id());
        assertTrue(first.rotates());
        state.advance(first.group());
        HighestPriorityRoundRobin.Selection<Option> second = select(recipes, state);
        assertEquals("mod_b:block", second.value().id());
        state.advance(second.group());
        HighestPriorityRoundRobin.Selection<Option> third = select(recipes, state);
        assertEquals("mod_c:block", third.value().id());
        state.advance(third.group());
        assertEquals("mod_a:block", select(recipes, state).value().id());
    }

    @Test
    void unrelatedConflictGroupsKeepIndependentCursors() {
        RecipeRoundRobinState state = new RecipeRoundRobinState();
        List<Option> firstGroup = List.of(new Option("a:one", 2), new Option("b:one", 2));
        List<Option> secondGroup = List.of(new Option("a:two", 2), new Option("b:two", 2));

        HighestPriorityRoundRobin.Selection<Option> first = select(firstGroup, state);
        state.advance(first.group());

        assertEquals("a:two", select(secondGroup, state).value().id());
        assertEquals("b:one", select(firstGroup, state).value().id());
    }

    private static HighestPriorityRoundRobin.Selection<Option> select(
            List<Option> options, RecipeRoundRobinState state) {
        return HighestPriorityRoundRobin.select(options, COMPLEXITY, Option::id, state);
    }

    private record Option(String id, int complexity) {
    }
}
