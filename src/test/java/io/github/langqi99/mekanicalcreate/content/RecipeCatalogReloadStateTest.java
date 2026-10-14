package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RecipeCatalogReloadStateTest {
    @Test
    void coalescesLoginRecipesTagsAndWorldIntoOneBuild() {
        var state = new RecipeCatalogReloadState();
        state.recipesUpdated();
        state.worldJoined();
        assertFalse(state.shouldBeginBuild());
        state.tagsUpdated();
        assertFalse(state.shouldBeginBuild());
        assertTrue(state.shouldBeginBuild());
        assertFalse(state.shouldBeginBuild());
    }

    @Test
    void preservesTagsReceivedBeforeLevelExists() {
        var state = new RecipeCatalogReloadState();
        state.recipesUpdated();
        state.tagsUpdated();
        state.worldJoined();
        assertFalse(state.shouldBeginBuild());
        assertTrue(state.shouldBeginBuild());
    }

    @Test
    void reloadWaitsForFreshTagsAndLogoutResetsReadiness() {
        var state = new RecipeCatalogReloadState();
        state.recipesUpdated();
        state.tagsUpdated();
        assertFalse(state.shouldBeginBuild());
        assertTrue(state.shouldBeginBuild());
        state.recipesUpdated();
        assertFalse(state.shouldBeginBuild());
        state.tagsUpdated();
        assertFalse(state.shouldBeginBuild());
        assertTrue(state.shouldBeginBuild());
        state.reset();
        state.worldJoined();
        assertFalse(state.shouldBeginBuild());
    }

    @Test
    void missingTagsCannotPostponeCatalogForever() {
        var state = new RecipeCatalogReloadState();
        state.recipesUpdated();
        for (int tick = 0; tick < 99; tick++) {
            state.worldJoined();
            assertFalse(state.shouldBeginBuild());
        }
        assertTrue(state.shouldBeginBuild());
        assertFalse(state.shouldBeginBuild());
    }

    @Test
    void initialTagsCannotBuildBeforeTheRecipePacketArrives() {
        var state = new RecipeCatalogReloadState();
        state.tagsUpdated();
        state.worldJoined();
        for (int tick = 0; tick < 200; tick++) assertFalse(state.shouldBeginBuild());
        state.recipesUpdated();
        assertFalse(state.shouldBeginBuild());
        assertTrue(state.shouldBeginBuild());
    }

    @Test
    void reloadAlsoAcceptsTagsBeforeRecipes() {
        var state = new RecipeCatalogReloadState();
        state.recipesUpdated();
        state.tagsUpdated();
        assertFalse(state.shouldBeginBuild());
        assertTrue(state.shouldBeginBuild());
        state.tagsUpdated();
        assertFalse(state.shouldBeginBuild());
        state.recipesUpdated();
        assertFalse(state.shouldBeginBuild());
        assertTrue(state.shouldBeginBuild());
    }

    @Test
    void tagsBeforeDelayedReloadRecipesCannotReuseThePreviousRecipeSignal() {
        var state = new RecipeCatalogReloadState();
        state.recipesUpdated();
        state.tagsUpdated();
        assertFalse(state.shouldBeginBuild());
        assertTrue(state.shouldBeginBuild());
        state.tagsUpdated();
        for (int tick = 0; tick < 20; tick++) assertFalse(state.shouldBeginBuild());
        state.recipesUpdated();
        assertFalse(state.shouldBeginBuild());
        assertTrue(state.shouldBeginBuild());
    }
}
