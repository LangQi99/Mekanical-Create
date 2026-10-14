package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeRegistrationQueueTest {
    @Test
    void publishesEveryRecipeExactlyOnceAcrossCategoryBoundary() {
        var queue = new RecipeRegistrationQueue<String>();
        queue.reset(1, List.of("a", "b", "c"), List.of("d", "e"));
        List<String> published = new ArrayList<>();
        while (!queue.isComplete()) {
            var batch = queue.nextBatch(2);
            published.addAll(batch.recipes());
            assertTrue(queue.complete(batch));
            assertFalse(queue.complete(batch));
        }
        assertEquals(List.of("a", "b", "c", "d", "e"), published);
        assertEquals(List.of("a", "b", "c"), queue.registeredItems());
        assertEquals(List.of("d", "e"), queue.registeredFluids());
        assertNull(queue.nextBatch(2));
    }

    @Test
    void failedBatchIsRetriedWithoutSkippingItsRecipes() {
        var queue = new RecipeRegistrationQueue<String>();
        queue.reset(1, List.of("a", "b"), List.of());
        var failed = queue.nextBatch(1);
        assertEquals(0, queue.registeredCount());
        assertSame(failed, queue.nextBatch(2));
        assertTrue(queue.complete(failed));
        assertEquals(List.of("b"), queue.nextBatch(1).recipes());
    }

    @Test
    void reloadRejectsOldBatchesAndReplacesPublishedContents() {
        var queue = new RecipeRegistrationQueue<String>();
        queue.reset(1, List.of("old"), List.of("old fluid"));
        assertTrue(queue.complete(queue.nextBatch(1)));
        var stale = queue.nextBatch(1);
        queue.reset(2, List.of("new"), List.of());
        assertFalse(queue.complete(stale));
        assertEquals(0, queue.registeredCount());
        assertEquals(List.of("new"), queue.nextBatch(10).recipes());
        assertTrue(queue.complete(queue.nextBatch(10)));
        assertTrue(queue.isComplete());
    }

    @Test
    void emptyCategoriesAndDisconnectHaveNoPendingWork() {
        var queue = new RecipeRegistrationQueue<String>();
        queue.reset(1, List.of(), List.of("fluid"));
        assertTrue(queue.nextBatch(3).fluid());
        queue.reset(0, List.of(), List.of());
        assertTrue(queue.isComplete());
        assertNull(queue.nextBatch(1));
        assertThrows(IllegalArgumentException.class, () -> queue.nextBatch(0));
    }
}
