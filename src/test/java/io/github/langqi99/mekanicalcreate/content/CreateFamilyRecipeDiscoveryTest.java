package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreateFamilyRecipeDiscoveryTest {
    private static final class SelfBound<T extends Comparable<T>> {
        T value;
    }

    private static final class MutualBounds<
            T extends Comparable<U>, U extends Comparable<T>> {
        T first;
        U second;
    }

    private static final class RecreatingWildcard implements WildcardType {
        @Override
        public Type[] getUpperBounds() {
            return new Type[]{new RecreatingWildcard()};
        }

        @Override
        public Type[] getLowerBounds() {
            return new Type[0];
        }
    }

    @Test
    void stopsAtSelfReferentialTypeVariable() throws NoSuchFieldException {
        Type type = SelfBound.class.getDeclaredField("value").getGenericType();
        Set<Class<?>> result = new LinkedHashSet<>();

        assertDoesNotThrow(() ->
                CreateFamilyRecipeDiscovery.visitGenericSignature(type, result::add));
    }

    @Test
    void stopsAtMutuallyReferentialTypeVariables() throws NoSuchFieldException {
        Type first = MutualBounds.class.getDeclaredField("first").getGenericType();
        Type second = MutualBounds.class.getDeclaredField("second").getGenericType();
        Set<Class<?>> result = new LinkedHashSet<>();

        assertDoesNotThrow(() -> {
            CreateFamilyRecipeDiscovery.visitGenericSignature(first, result::add);
            CreateFamilyRecipeDiscovery.visitGenericSignature(second, result::add);
        });
    }

    @Test
    void depthLimitStopsRecreatedRecursiveTypes() {
        Set<Class<?>> result = new LinkedHashSet<>();

        assertDoesNotThrow(() -> CreateFamilyRecipeDiscovery.visitGenericSignature(
                new RecreatingWildcard(), result::add));
    }
}
