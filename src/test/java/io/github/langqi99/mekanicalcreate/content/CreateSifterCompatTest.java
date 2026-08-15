package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CreateSifterCompatTest {
    @Test
    void recognizesOnlyTheTwoSifterModules() {
        assertTrue(CreateSifterIds.isSifterModule("createsifter", "sifter"));
        assertTrue(CreateSifterIds.isSifterModule("createsifter", "brass_sifter"));
        assertFalse(CreateSifterIds.isSifterModule("createsifter", "andesite_mesh"));
        assertFalse(CreateSifterIds.isSifterModule("another_mod", "sifter"));
    }

    @Test
    void recognizesTheRealRecipeTypeAndLegacyAlias() {
        assertTrue(CreateSifterIds.isSiftingRecipeType("createsifter", "sifting_type"));
        assertTrue(CreateSifterIds.isSiftingRecipeType("createsifter", "sifting"));
        assertFalse(CreateSifterIds.isSiftingRecipeType("createsifter", "sifting_recipe"));
        assertFalse(CreateSifterIds.isSiftingRecipeType("another_mod", "sifting_type"));
    }
}
